package com.payorch.reconciliation.step;

import com.payorch.reconciliation.domain.MismatchType;
import com.payorch.reconciliation.domain.ReconciliationMismatch;
import com.payorch.reconciliation.repository.SettlementRecordRepository;
import com.payorch.shared.model.SettlementRecord;
import com.payorch.shared.model.Transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemProcessor implements ItemProcessor<Transaction, List<ReconciliationMismatch>> {

    private static final String PENDING_INVESTIGATION = "PENDING_INVESTIGATION";
    private static final String UNKNOWN_EXTERNAL_STATUS = "UNKNOWN";

    private final SettlementRecordRepository settlementRepository;

    @Override
    public List<ReconciliationMismatch> process(Transaction txn) {
        log.debug("Executing settlement verification check for Transaction: {}", txn.getId());

        SettlementRecord settlement = settlementRepository.findByProviderRefId(txn.getProviderRefId()).orElse(null);
        if (settlement == null) {
            log.warn("Reconciliation mismatch detected. type={}, transactionId={}, providerRefId={}",
                    MismatchType.MISSING_PROVIDER_RECORD, txn.getId(), txn.getProviderRefId());
            return List.of(buildMismatch(txn, UNKNOWN_EXTERNAL_STATUS, MismatchType.MISSING_PROVIDER_RECORD));
        }

        List<ReconciliationMismatch> mismatches = new ArrayList<>();

        if (!txn.getStatus().name().equals(settlement.getExternalStatus())) {
            log.warn(
                    "Reconciliation mismatch detected. type={}, transactionId={}, internalStatus={}, providerStatus={}",
                    MismatchType.STATUS_MISMATCH, txn.getId(), txn.getStatus().name(), settlement.getExternalStatus());
            mismatches.add(buildMismatch(txn, settlement.getExternalStatus(), MismatchType.STATUS_MISMATCH));
        }

        if (amountsDiffer(txn.getAmount(), settlement.getGrossAmount())) {
            log.warn(
                    "Reconciliation mismatch detected. type={}, transactionId={}, internalAmount={}, providerAmount={}",
                    MismatchType.AMOUNT_MISMATCH, txn.getId(), txn.getAmount(), settlement.getGrossAmount());
            mismatches.add(buildMismatch(txn, settlement.getExternalStatus(), MismatchType.AMOUNT_MISMATCH));
        }

        BigDecimal expectedNet = settlement.getGrossAmount().subtract(settlement.getFeeAmount());
        if (expectedNet.compareTo(settlement.getNetAmount()) != 0) {
            log.warn(
                    "Reconciliation mismatch detected. type={}, transactionId={}, expectedNet={}, recordedNet={}",
                    MismatchType.FEE_CALCULATION_ERROR, txn.getId(), expectedNet, settlement.getNetAmount());
            mismatches.add(buildMismatch(txn, settlement.getExternalStatus(), MismatchType.FEE_CALCULATION_ERROR));
        }

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

    private boolean amountsDiffer(BigDecimal internalAmount, BigDecimal providerAmount) {
        if (internalAmount == null || providerAmount == null) {
            return false;
        }
        return internalAmount.compareTo(providerAmount) != 0;
    }
}
