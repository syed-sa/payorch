// File: src/main/java/com/payorch/orchestrator/service/PaymentOrchestrator.java
package com.payorch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.common.idempotency.IdempotencyManager;
import com.payorch.shared.dto.PaymentExecutionRequest;
import com.payorch.shared.dto.ProviderResponse;
import com.payorch.shared.dto.ProviderStatus;
import com.payorch.shared.model.Transaction;
import com.payorch.shared.service.PaymentProvider;

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

            // 5. Store Final Idempotency Payload on Success/Controlled failure
            idempotencyManager.saveResponse(key, objectMapper.writeValueAsString(response));
            return response;

        } catch (Exception e) {
            log.error("Fatal platform orchestration exception for txn: {}", transaction.getId(), e);
            stateManager.handleLocalFailureState(transaction, "FATAL_ORCHESTRATION_ERROR: " + e.getMessage());

            ProviderResponse failResponse = ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();

            try {
                idempotencyManager.saveResponse(key, objectMapper.writeValueAsString(failResponse));
            } catch (Exception ex) {
                log.error("Failed to cache execution failure status", ex);
            }
            return failResponse;
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

            // Record successful/handled response to DB & Outbox
            stateManager.finalizePaymentState(transaction, response);
            return response;

        } catch (Exception e) {
            log.warn("Provider {} failed or circuit is open! Error: {}. Initiating cascading failover...", providerId,
                    e.getMessage());

            // If we have exhausted all available payment providers, bubble up the failure
            if (routingStrategy.getAvailableProviderCount() <= attemptedProviderIds.size()) {
                throw new RuntimeException("All downstream payment providers have been completely exhausted.", e);
            }

            // RECURSIVE FAILOVER: Try the next best provider smoothly
            return executeWithFailover(transaction, paymentMethodToken, attemptedProviderIds);
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
