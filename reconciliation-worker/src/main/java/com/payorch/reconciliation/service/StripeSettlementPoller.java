package com.payorch.reconciliation.service;

import com.payorch.reconciliation.repository.SettlementRecordRepository;
import com.payorch.shared.model.SettlementRecord;
import com.stripe.Stripe;
import com.stripe.model.BalanceTransaction;
import com.stripe.param.BalanceTransactionListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeSettlementPoller {

    private final SettlementRecordRepository settlementRecordRepository;

    @Value("${payment.stripe.api-key:}")
    private String apiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }

    public void syncSettlementsForWindow(Instant start, Instant end) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Stripe API key not configured; skipping settlement sync");
            return;
        }

        try {
            BalanceTransactionListParams.Created created = BalanceTransactionListParams.Created.builder()
                    .setGt(start.getEpochSecond())
                    .setLt(end.getEpochSecond())
                    .build();

            BalanceTransactionListParams params = BalanceTransactionListParams.builder()
                    .setCreated(created)
                    .setType("charge")
                    .build();

            Iterable<BalanceTransaction> transactions = BalanceTransaction.list(params).autoPagingIterable();

            for (BalanceTransaction bt : transactions) {
                SettlementRecord record = SettlementRecord.builder()
                        .providerRefId(bt.getSource())
                        .grossAmount(toDecimal(bt.getAmount()))
                        .feeAmount(toDecimal(bt.getFee()))
                        .netAmount(toDecimal(bt.getNet()))
                        .externalStatus("SUCCESS")
                        .build();

                settlementRecordRepository.findByProviderRefId(bt.getSource())
                        .ifPresentOrElse(existing -> {
                            existing.setGrossAmount(record.getGrossAmount());
                            existing.setFeeAmount(record.getFeeAmount());
                            existing.setNetAmount(record.getNetAmount());
                            existing.setExternalStatus(record.getExternalStatus());
                            settlementRecordRepository.save(existing);
                        }, () -> settlementRecordRepository.save(record));
            }

            log.info("Synced Stripe settlement records for window {} to {}", start, end);
        } catch (Exception e) {
            log.error("Failed to sync Stripe settlement records", e);
        }
    }

    public void syncLatestWindow() {
        Instant end = Instant.now();
        Instant start = end.minus(24, ChronoUnit.HOURS);
        syncSettlementsForWindow(start, end);
    }

    private BigDecimal toDecimal(Long value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
}
