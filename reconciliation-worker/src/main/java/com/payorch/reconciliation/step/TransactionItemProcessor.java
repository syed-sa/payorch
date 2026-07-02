package com.payorch.reconciliation.step;

import com.payorch.providers.factory.PaymentProviderFactory;
import com.payorch.reconciliation.domain.MismatchType;
import com.payorch.reconciliation.domain.ReconciliationMismatch;
import com.payorch.shared.dto.ProviderTransactionDetails;
import com.payorch.shared.exception.ProviderStatusException;
import com.payorch.shared.model.Transaction;
import com.payorch.shared.service.PaymentProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemProcessor implements ItemProcessor<Transaction, List<ReconciliationMismatch>> {

    private static final String PENDING_INVESTIGATION = "PENDING_INVESTIGATION";
    private static final String UNKNOWN_EXTERNAL_STATUS = "UNKNOWN";

    // In a production setup, inject your Strategy Factory here to switch between
    // Stripe and Razorpay Client APIs
    private final PaymentProviderFactory providerFactory;

    @Override
    public List<ReconciliationMismatch> process(Transaction txn) {
        log.debug("Executing status verification check for Transaction: {}", txn.getId());

        PaymentProvider provider = providerFactory.get(txn.getProviderId());

        ProviderTransactionDetails details;
        try {
            details = provider.fetchStatus(txn.getProviderRefId());
        } catch (ProviderStatusException e) {
            log.warn("Reconciliation mismatch detected. type={}, transactionId={}, providerRefId={}",
                    MismatchType.MISSING_PROVIDER_RECORD, txn.getId(), txn.getProviderRefId());
            return List.of(buildMismatch(txn, UNKNOWN_EXTERNAL_STATUS, MismatchType.MISSING_PROVIDER_RECORD));
        }

        if (details == null || (details.getStatus() == null && details.getExternalStatus() == null)) {
            log.warn("Reconciliation mismatch detected. type={}, transactionId={}, providerRefId={}",
                    MismatchType.MISSING_PROVIDER_RECORD, txn.getId(), txn.getProviderRefId());
            return List.of(buildMismatch(txn, UNKNOWN_EXTERNAL_STATUS, MismatchType.MISSING_PROVIDER_RECORD));
        }

        List<ReconciliationMismatch> mismatches = new ArrayList<>();
        String providerStatus = providerStatus(details);

        // Cross-check: compare internal orchestration state against the provider state.
        if (!txn.getStatus().name().equals(providerStatus)) {
            log.warn(
                    "Reconciliation mismatch detected. type={}, transactionId={}, internalStatus={}, providerStatus={}",
                    MismatchType.STATUS_MISMATCH, txn.getId(), txn.getStatus().name(), providerStatus);
            mismatches.add(buildMismatch(txn, providerStatus, MismatchType.STATUS_MISMATCH));
        }

        if (amountsDiffer(txn.getAmount(), details.getAmount())) {
            log.warn(
                    "Reconciliation mismatch detected. type={}, transactionId={}, internalAmount={}, providerAmount={}",
                    MismatchType.AMOUNT_MISMATCH, txn.getId(), txn.getAmount(), details.getAmount());
            mismatches.add(buildMismatch(txn, providerStatus, MismatchType.AMOUNT_MISMATCH));
        }

        // If records are fully aligned, return null. Spring Batch filters out null
        // entries from writing out.
        return mismatches.isEmpty() ? null : mismatches;
    }

    private ReconciliationMismatch buildMismatch(Transaction txn, String externalStatus, MismatchType mismatchType) {
        return ReconciliationMismatch.builder()
                .transactionId(txn.getId())
                .providerRefId(txn.getProviderRefId())
                .internalStatus(txn.getStatus().name())
                .externalStatus(externalStatus)
                .mismatchType(mismatchType)
                .resolutionStatus(PENDING_INVESTIGATION)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String providerStatus(ProviderTransactionDetails details) {
        if (details.getStatus() != null) {
            return details.getStatus().name();
        }
        return details.getExternalStatus();
    }

    private boolean amountsDiffer(BigDecimal internalAmount, BigDecimal providerAmount) {
        if (internalAmount == null || providerAmount == null) {
            return false;
        }
        return internalAmount.compareTo(providerAmount) != 0;
    }
}
