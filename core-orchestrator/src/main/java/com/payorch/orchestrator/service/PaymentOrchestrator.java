// File: src/main/java/com/payorch/orchestrator/service/PaymentOrchestrator.java
package com.payorch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.common.idempotency.IdempotencyManager;
import com.payorch.shared.contract.PaymentProvider;
import com.payorch.shared.dto.PaymentExecutionRequest;
import com.payorch.shared.dto.ProviderResponse;
import com.payorch.shared.dto.ProviderStatus;
import com.payorch.shared.exception.BusinessException;
import com.payorch.shared.exception.InfrastructureException;
import com.payorch.shared.exception.RetryableException;
import com.payorch.shared.model.Transaction;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOrchestrator {

    private final SmartRoutingStrategy routingStrategy;
    private final IdempotencyManager idempotencyManager;
    private final PaymentStateManager stateManager;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ProviderResponse processPayment(Transaction transaction, String paymentMethodToken) {
        String key = transaction.getIdempotencyKey();

        // 1. Idempotency Check
        String cachedResponse = idempotencyManager.getResponse(key);
        if (cachedResponse != null) {
            log.info("Returning cached response for idempotency key: {}", key);
            return deserialize(cachedResponse);
        }

        // 2. Concurrency Lock
        if (!idempotencyManager.acquireLock(key)) {
            throw new IllegalStateException("Transaction already in progress for key: " + key);
        }

        try {
            // 3. Keep track of tried providers to avoid looping back to a failing one
            // during failover
            List<String> attemptedProviderIds = new ArrayList<>();

            // 4. Execute external payment with dynamic failover capabilities
            ProviderResponse response = executeWithFailover(transaction, paymentMethodToken, attemptedProviderIds);

            if (response.isFinalResponse()) {
                // 5. Cache only definite or in-flight provider outcomes
                idempotencyManager.saveResponse(key, objectMapper.writeValueAsString(response));
            } else if (response.isTransientFailure()) {
                stateManager.handleTransientFailureState(transaction,
                        "TRANSIENT_PAYMENT_FAILURE: " + response.getErrorMessage());
            }
            return response;

        } catch (BusinessException e) {
            log.error("Final business failure for txn: {}", transaction.getId(), e);
            stateManager.handleLocalFailureState(transaction, "BUSINESS_FAILURE: " + e.getMessage());

            ProviderResponse failResponse = ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .finalResponse(true)
                    .build();

            try {
                idempotencyManager.saveResponse(key, objectMapper.writeValueAsString(failResponse));
            } catch (Exception ex) {
                log.error("Failed to cache final business failure response for key: {}", key, ex);
            }
            return failResponse;

        } catch (RetryableException | InfrastructureException e) {
            log.warn("Transient payment failure for txn {}: {}", transaction.getId(), e.getMessage());
            stateManager.handleTransientFailureState(transaction, "TRANSIENT_FAILURE: " + e.getMessage());

            return ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .finalResponse(false)
                    .build();
        } catch (Exception e) {
            log.error("Unexpected payment execution failure for txn {}", transaction.getId(), e);
            stateManager.handleTransientFailureState(transaction, "UNEXPECTED_FAILURE: " + e.getMessage());

            return ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage("UNEXPECTED_FAILURE: " + e.getMessage())
                    .finalResponse(false)
                    .build();
        } finally {
            idempotencyManager.releaseLock(key);
        }
    }

    /**
     * Executes the provider call inside a resilience wrapper.
     * If a provider fails or its circuit is open, it catches the error and
     * dynamically retries with a backup.
     */
    private ProviderResponse executeWithFailover(
            Transaction transaction,
            String paymentMethodToken,
            List<String> attemptedProviderIds) {
        // Get the best provider excluding ones we already tried in this request cycle
        PaymentProvider provider = routingStrategy.selectBestProviderExcluding(attemptedProviderIds);
        String providerId = provider.getProviderId();
        attemptedProviderIds.add(providerId);

        // Record the intent to the database (isolated transaction, no network held
        // open)
        stateManager.initializePaymentState(transaction, providerId);

        // Get or create a circuit breaker specifically for this payment provider
        // instance
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentProviderCircuit-" + providerId);

        try {
            log.info("Attempting execution via provider: {} for txn: {}", providerId, transaction.getId());

            // Decorate and execute the network call inside the Resilience4j Circuit Breaker
            // context
            PaymentExecutionRequest request = new PaymentExecutionRequest(transaction, paymentMethodToken);
            ProviderResponse response = circuitBreaker.executeSupplier(() -> provider.process(request));

            if (response.isTransientFailure()) {
                throw new RetryableException("Transient provider failure for provider " + providerId + ": "
                        + response.getErrorMessage());
            }
 
            // Record successful/handled response to DB & Outbox
            stateManager.finalizePaymentState(transaction, response);
            return response;

        } catch (BusinessException e) {
            log.warn("Provider {} reported a final business failure for txn {}: {}", providerId,
                    transaction.getId(), e.getMessage());
            ProviderResponse failureResponse = ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .finalResponse(true)
                    .build();
            stateManager.finalizePaymentState(transaction, failureResponse);
            return failureResponse;

        } catch (RetryableException | InfrastructureException e) {
            log.warn("Provider {} failed or circuit is open! Error: {}. Initiating cascading failover...", providerId,
                    e.getMessage());

            if (routingStrategy.getAvailableProviderCount() <= attemptedProviderIds.size()) {
                throw new RetryableException("All downstream payment providers have been completely exhausted.", e);
            }

            return executeWithFailover(transaction, paymentMethodToken, attemptedProviderIds);
        } catch (Exception e) {
            throw new InfrastructureException("Unexpected provider execution failure", e);
        }
    }

    private ProviderResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProviderResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode payload template", e);
        }
    }
}
