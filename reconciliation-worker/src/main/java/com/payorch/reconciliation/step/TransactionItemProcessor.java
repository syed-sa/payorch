package com.payorch.reconciliation.step;

import com.payorch.model.Transaction;
import com.payorch.model.TransactionStatus;
import com.payorch.providers.dto.ProviderTransactionDetails;
import com.payorch.providers.factory.PaymentProviderFactory;
import com.payorch.providers.service.PaymentProvider;
import com.payorch.reconciliation.domain.ReconciliationMismatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemProcessor implements ItemProcessor<Transaction, ReconciliationMismatch> {

    // In a production setup, inject your Strategy Factory here to switch between
    // Stripe and Razorpay Client APIs
    private final PaymentProviderFactory providerFactory;

    @Override
    public ReconciliationMismatch process(Transaction txn) {
        log.debug("Executing status verification check for Transaction: {}", txn.getId());

        PaymentProvider provider = providerFactory.get(txn.getProviderId());

        ProviderTransactionDetails details = provider.fetchStatus(txn.getProviderRefId());

        // Cross-check: Compare our database representation against the true ledger
        // statement from the bank
        if (TransactionStatus.PENDING.name().equals(txn.getStatus().name())
                && "SUCCESS".equals(details.getExternalStatus())) {
            log.warn(
                    "CRITICAL STATE DISCREPANCY ENCOUNTERED! Transaction {} is PENDING locally but SUCCESS on provider gateway.",
                    txn.getId());

            return ReconciliationMismatch.builder()
                    .transactionId(txn.getId())
                    .providerRefId(txn.getProviderRefId())
                    .internalStatus(txn.getStatus().name())
                    .externalStatus(details.getExternalStatus())
                    .resolutionStatus("PENDING_INVESTIGATION")
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        // If records are fully aligned, return null. Spring Batch filters out null
        // entries from writing out.
        return null;
    }
}