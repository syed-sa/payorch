// File: core-orchestrator/src/main/java/com/payorch/orchestrator/service/PaymentStateManager.java
package com.payorch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.orchestrator.repository.TransactionRepository;
import com.payorch.outbox.model.OutboxEvent;
import com.payorch.outbox.repository.OutboxRepository;
import com.payorch.shared.model.Transaction;
import com.payorch.shared.model.TransactionStatus;
import com.payorch.shared.providers.dto.ProviderResponse;
import com.payorch.shared.providers.dto.ProviderStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStateManager {

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void initializePaymentState(Transaction transaction, String providerId) {
        transaction.setProviderId(providerId);
        transaction.setStatus(TransactionStatus.INITIATED);
        transactionRepository.saveAndFlush(transaction);
        createOutboxEvent("PAYMENT_INITIATED", transaction);
    }

    @Transactional
    public void finalizePaymentState(Transaction txn, ProviderResponse res) {
        if (ProviderStatus.SUCCESS.equals(res.getStatus())) {
            txn.setStatus(TransactionStatus.SUCCESS);
            txn.setProviderRefId(res.getExternalId());
        } else if (ProviderStatus.PENDING.equals(res.getStatus())) {
            txn.setStatus(TransactionStatus.PENDING);
            txn.setProviderRefId(res.getExternalId());
        } else {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason(res.getErrorMessage());
        }
        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(txn);

        createOutboxEvent(eventTypeFor(txn.getStatus()), txn);
    }

    @Transactional
    public void handleLocalFailureState(Transaction txn, String message) {
        txn.setStatus(TransactionStatus.FAILED);
        txn.setFailureReason(message);
        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(txn);
        
        createOutboxEvent("PAYMENT_FAILED", txn);
    }

    /**
     * PROCESSES INBOUND WEBHOOK STATE TRANSITIONS (Called by Kafka Consumers)
     * Matches transactions using the unique provider reference identification key.
     */
    @Transactional
    public void processWebhookStateTransition(String providerRefId, String targetStatus, String failureReason) {
        log.info("Processing provider webhook state transition for Provider Ref: {}", providerRefId);

        Transaction txn = transactionRepository.findByProviderRefId(providerRefId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No transaction found matching Provider Reference: " + providerRefId));

        if (TransactionStatus.SUCCESS.equals(txn.getStatus())
                || TransactionStatus.FAILED.equals(txn.getStatus())
                || TransactionStatus.REFUNDED.equals(txn.getStatus())) {
            log.info("Transaction {} is already in final state ({}). Discarding duplicate webhook event.",
                    txn.getId(), txn.getStatus());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(targetStatus)) {
            txn.setStatus(TransactionStatus.SUCCESS);
            log.info("Transitioning transaction state to SUCCESS for system record tracking ID: {}", txn.getId());
        } else if ("FAILED".equalsIgnoreCase(targetStatus)) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason(failureReason != null ? failureReason : "WEBHOOK_ASYNC_GATEWAY_FAILURE_REPORT");
            log.warn("Transitioning transaction state to FAILED for system record tracking ID: {}. Reason: {}", txn.getId(), failureReason);
        } else if ("PENDING".equalsIgnoreCase(targetStatus)) {
            txn.setStatus(TransactionStatus.PENDING);
            log.info("Transitioning transaction state to PENDING for system record tracking ID: {}", txn.getId());
        } else if ("REFUNDED".equalsIgnoreCase(targetStatus)) {
            txn.setStatus(TransactionStatus.REFUNDED);
            log.info("Transitioning transaction state to REFUNDED for system record tracking ID: {}", txn.getId());
        } else {
            log.info("Webhook event contains unsupported state tracking update ({}). No state transition needed for tracking ID: {}",
                    targetStatus, txn.getId());
            return;
        }

        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepository.saveAndFlush(txn);

        createOutboxEvent(eventTypeFor(txn.getStatus()), txn);
        log.info("Webhook state synchronization committed for transaction tracking ID: {}", txn.getId());
    }

    private void createOutboxEvent(String eventType, Transaction transaction) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("transactionId", transaction.getId());
            payload.put("idempotencyKey", transaction.getIdempotencyKey());
            payload.put("status", transaction.getStatus());
            payload.put("amount", transaction.getAmount());
            payload.put("currency", transaction.getCurrency());
            payload.put("providerId", transaction.getProviderId());
            payload.put("providerRefId", transaction.getProviderRefId());
            payload.put("merchantId", transaction.getMerchantId());
            payload.put("customerReference", transaction.getCustomerReference());
            payload.put("failureReason", transaction.getFailureReason());
            payload.put("createdAt", transaction.getCreatedAt());
            payload.put("updatedAt", transaction.getUpdatedAt());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxEvent event = OutboxEvent.builder()
                    .eventType(eventType)
                    .payload(jsonPayload)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
            outboxRepository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write to Outbox table", e);
        }
    }

    private String eventTypeFor(TransactionStatus status) {
        return switch (status) {
            case INITIATED -> "PAYMENT_INITIATED";
            case PENDING -> "PAYMENT_PENDING";
            case SUCCESS -> "PAYMENT_SUCCEEDED";
            case FAILED -> "PAYMENT_FAILED";
            case REFUNDED -> "PAYMENT_REFUNDED";
        };
    }
    
}