package com.payorch.reconciliation.service;

import com.payorch.reconciliation.repository.SettlementRecordRepository;
import com.payorch.shared.model.SettlementRecord;
import com.razorpay.RazorpayClient;
import com.razorpay.Settlement;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RazorpaySettlementPoller implements SettlementPoller {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_FETCH_ATTEMPTS = 3;

    private final SettlementRecordRepository settlementRecordRepository;

    @Value("${payment.razorpay.key-id:}")
    private String keyId;

    @Value("${payment.razorpay.key-secret:}")
    private String keySecret;

    @Value("${payment.razorpay.settlement-lag-days:3}")
    private int settlementLagDays;

    private RazorpayClient client;

    @PostConstruct
    void init() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            log.warn("Razorpay API credentials are not configured; settlement sync will be skipped");
            return;
        }

        try {
            client = new RazorpayClient(keyId, keySecret);
        } catch (Exception e) {
            log.error("Unable to initialize Razorpay client for settlement sync", e);
        }
    }

    @Override
    public String providerName() {
        return "RAZORPAY";
    }

    @Override
    public void syncLatestWindow() {
        syncSettlementsForDate(LocalDate.now().minusDays(settlementLagDays));
    }

    public void syncSettlementsForDate(LocalDate date) {
        if (client == null) {
            log.warn("Razorpay client is unavailable; skipping settlement sync for {}", date);
            return;
        }

        int skip = 0;
        try {
            while (true) {
                List<Settlement> page;
                try {
                    page = fetchPageWithRetry(date, skip);
                } catch (Exception e) {
                    log.error("Failed to fetch Razorpay settlement page for date {} at skip {}", date, skip, e);
                    throw e;
                }

                for (Settlement settlement : page) {
                    SettlementRecord record = mapPaymentSettlement(settlement);
                    if (record == null) {
                        continue;
                    }
                    upsert(record);
                }

                if (page.size() < PAGE_SIZE) {
                    break;
                }
                skip += PAGE_SIZE;
            }
            log.info("Synced Razorpay settlement records for date {}", date);
        } catch (Exception e) {
            log.error("Failed to sync Razorpay settlement records for date {}", date, e);
        }
    }

    private List<Settlement> fetchPageWithRetry(LocalDate date, int skip) throws Exception {
        JSONObject params = new JSONObject()
                .put("year", date.getYear())
                .put("month", date.getMonthValue())
                .put("day", date.getDayOfMonth())
                .put("count", PAGE_SIZE)
                .put("skip", skip);

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt++) {
            try {
                List<Settlement> settlements = client.settlement.reports(params);
                return settlements == null ? Collections.emptyList() : settlements;
            } catch (Exception e) {
                lastFailure = e;
                if (attempt == MAX_FETCH_ATTEMPTS) {
                    break;
                }
                long backoffMillis = 250L * (1L << (attempt - 1));
                log.warn("Razorpay settlement fetch failed for date {} at skip {} (attempt {}/{}); retrying in {} ms",
                        date, skip, attempt, MAX_FETCH_ATTEMPTS, backoffMillis, e);
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
        throw lastFailure;
    }

    SettlementRecord mapPaymentSettlement(Settlement settlement) {
        if (!"payment".equals(settlement.get("type"))) {
            return null;
        }

        String providerRefId = settlement.get("entity_id");
        if (providerRefId == null || providerRefId.isBlank()) {
            log.warn("Skipping Razorpay payment settlement item without an entity_id");
            return null;
        }

        BigDecimal credit = toRupees(settlement.get("credit"));
        BigDecimal debit = toRupees(settlement.get("debit"));
        BigDecimal fee = toRupees(settlement.get("fee"));
        BigDecimal gross = toRupees(settlement.get("amount"));

        return SettlementRecord.builder()
                .providerRefId(providerRefId)
                .grossAmount(gross)
                .feeAmount(fee)
                .netAmount(credit.subtract(debit).setScale(2, RoundingMode.HALF_UP))
                .externalStatus("SUCCESS")
                .build();
    }

    private void upsert(SettlementRecord record) {
        settlementRecordRepository.findByProviderRefId(record.getProviderRefId())
                .ifPresentOrElse(existing -> {
                    existing.setGrossAmount(record.getGrossAmount());
                    existing.setFeeAmount(record.getFeeAmount());
                    existing.setNetAmount(record.getNetAmount());
                    existing.setExternalStatus(record.getExternalStatus());
                    settlementRecordRepository.save(existing);
                }, () -> settlementRecordRepository.save(record));
    }

    private BigDecimal toRupees(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal paise = value instanceof Number number
                ? new BigDecimal(number.toString())
                : new BigDecimal(value.toString());
        return paise.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
