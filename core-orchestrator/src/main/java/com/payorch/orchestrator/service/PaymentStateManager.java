// File: src/main/java/com/payorch/orchestrator/service/PaymentStateManager.java
package com.payorch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.ledger.model.Transaction;
import com.payorch.ledger.model.TransactionStatus;
import com.payorch.ledger.repository.TransactionRepository;
import com.payorch.outbox.model.OutboxEvent;
import com.payorch.outbox.repository.OutboxRepository;
import com.payorch.providers.dto.ProviderResponse;
import com.payorch.providers.dto.ProviderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

        // Write to Outbox for downstream Ledger/Notification consumption (SAGA Pattern)
        createOutboxEvent("PAYMENT_FINALIZED", txn);
    }

    @Transactional
    public void handleLocalFailureState(Transaction txn, String message) {
        txn.setStatus(TransactionStatus.FAILED);
        txn.setFailureReason(message);
        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(txn);
        
        createOutboxEvent("PAYMENT_FAILED", txn);
    }

    private void createOutboxEvent(String eventType, Object payloadData) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadData);
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
}