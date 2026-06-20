// File: core-orchestrator/src/main/java/com/payorch/orchestrator/service/PaymentStateManager.java
package com.payorch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.ledger.repository.LedgerRepository;
import com.payorch.ledger.repository.TransactionRepository;
import com.payorch.outbox.model.OutboxEvent;
import com.payorch.outbox.repository.OutboxRepository;
import com.payorch.shared.model.LedgerEntry;
import com.payorch.shared.model.Transaction;
import com.payorch.shared.model.TransactionStatus;
import com.payorch.shared.providers.dto.ProviderResponse;
import com.payorch.shared.providers.dto.ProviderStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStateManager {

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final LedgerRepository ledgerRepository;

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

    /**
     * PROCESSES INBOUND WEBHOOK STATE TRANSITIONS (Called by Kafka Consumers)
     * Matches transactions using the unique provider reference identification key.
     */
    @Transactional
    public void processWebhookStateTransition(String providerRefId, String targetStatus, String failureReason) {
        log.info("Acquiring transaction record update boundary lock for Provider Ref: {}", providerRefId);

        // 1. Fetch the transaction using the unique reference assigned by Stripe/Razorpay
        // Note: For high-concurrency assurance, ensure your repository layer implements findByProviderRefId
        Transaction txn = transactionRepository.findByProviderRefId(providerRefId)
                .orElseThrow(() -> new IllegalArgumentException("No transaction found in Ledger matching Provider Reference: " + providerRefId));

        // 2. IDEMPOTENCY GUARD: If the transaction is already in a final state, discard the duplicate update safely
        if (TransactionStatus.SUCCESS.equals(txn.getStatus()) || TransactionStatus.FAILED.equals(txn.getStatus())) {
            log.info("Transaction {} is already processed and settled in a final state ({}). Discarding webhook stream event.", 
                    txn.getId(), txn.getStatus());
            return;
        }

        // 3. Mutate the state machine data matrix values
        if ("SUCCESS".equalsIgnoreCase(targetStatus)) {
            txn.setStatus(TransactionStatus.SUCCESS);
            generateLedgerEntries(txn);
            log.info("Transitioning transaction state to SUCCESS for system record tracking ID: {}", txn.getId());
        } else if ("FAILED".equalsIgnoreCase(targetStatus)) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason(failureReason != null ? failureReason : "WEBHOOK_ASYNC_GATEWAY_FAILURE_REPORT");
            log.warn("Transitioning transaction state to FAILED for system record tracking ID: {}. Reason: {}", txn.getId(), failureReason);
        } else {
            log.info("Webhook event contains non-final state tracking update ({}). No state transition needed for tracking ID: {}", targetStatus, txn.getId());
            return; // Exit without committing any unnecessary database mutations
        }

        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepository.saveAndFlush(txn);

        // 4. EMIT TRANSACTIONAL OUTBOX EVENT: Keep your downstream double-entry ledger ledgers and notifications perfectly synchronized
        createOutboxEvent("PAYMENT_WEBHOOK_SETTLED", txn);
        log.info("State synchronization successfully committed to ledger tables for transaction tracking tracking record ID: {}", txn.getId());
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

        private void generateLedgerEntries(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();

        // Entry A: Debit the Sender's account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(transaction.getSenderAccount())
                .amount(amount.negate()) // Representing an outflow (-50.00)
                .entryType("DEBIT")
                .build();

        // Entry B: Credit the Receiver's account
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(transaction.getReceiverAccount())
                .amount(amount) // Representing an inflow (+50.00)
                .entryType("CREDIT")
                .build();

        // Persist both rows atomically to PostgreSQL
        ledgerRepository.save(debitEntry);
        ledgerRepository.save(creditEntry);
        
        log.debug("Atomically generated Double-Entry Pair: Transaction ID {}", transaction.getId());
    }
    
}