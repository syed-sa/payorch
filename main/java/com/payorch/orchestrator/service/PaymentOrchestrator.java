// File: src/main/java/com/payorch/orchestrator/service/PaymentOrchestrator.java
package com.payorch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.ledger.model.Transaction;
import com.payorch.ledger.model.TransactionStatus;
import com.payorch.ledger.repository.TransactionRepository;
import com.payorch.common.idempotency.IdempotencyManager;
import com.payorch.providers.service.PaymentProvider;
import com.payorch.providers.dto.ProviderResponse;
import com.payorch.providers.dto.ProviderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOrchestrator {

    private final SmartRoutingStrategy routingStrategy;
    private final IdempotencyManager idempotencyManager;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void processPayment(Transaction transaction) {
        String key = transaction.getIdempotencyKey();

        // 1. Idempotency Check (Redis Read)
        String cachedResponse = idempotencyManager.getResponse(key);
        if (cachedResponse != null) {
            log.info("Returning cached response for idempotency key: {}", key);
            return;
        }

        // 2. Concurrency Control (Redis Lock)
        if (!idempotencyManager.acquireLock(key)) {
            throw new IllegalStateException("Transaction already in progress for key: " + key);
        }

        try {
            // 3. Smart Routing
            PaymentProvider provider = routingStrategy.selectBestProvider();
            
            // Update transaction with chosen provider before execution
            transaction.setProviderId(provider.getProviderId());
            transaction.setStatus(TransactionStatus.INITIATED);
            transactionRepository.saveAndFlush(transaction); 

            // 4. Provider Execution
            ProviderResponse response = provider.process(transaction);

            // 5. Update Final State
            finalizeTransaction(transaction, response);
            
            // 6. Cache response for future duplicate requests
            idempotencyManager.saveResponse(key, objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            log.error("Payment orchestration failed for txn: {}", transaction.getId(), e);
            handleFailure(transaction, e);
            // Release lock so client can retry if it was a system failure
            idempotencyManager.releaseLock(key);
        }
    }

    private void finalizeTransaction(Transaction txn, ProviderResponse res) {
        // Checking against the Enum or using the helper method from step 1
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
        transactionRepository.save(txn);
    }

    private void handleFailure(Transaction txn, Exception e) {
        txn.setStatus(TransactionStatus.FAILED);
        txn.setFailureReason("SYSTEM_ERROR: " + e.getMessage());
        transactionRepository.save(txn);
    }
}