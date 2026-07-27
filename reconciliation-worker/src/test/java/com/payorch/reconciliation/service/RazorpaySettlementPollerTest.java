package com.payorch.reconciliation.service;

import com.payorch.reconciliation.repository.SettlementRecordRepository;
import com.payorch.shared.model.SettlementRecord;
import com.razorpay.Settlement;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RazorpaySettlementPollerTest {

    private final RazorpaySettlementPoller poller = new RazorpaySettlementPoller(mock(SettlementRecordRepository.class));

    @Test
    void mapsCapturedReconPaymentJsonAmountsFromPaiseToRupees() {
        Settlement settlement = new Settlement(new JSONObject("""
                {
                  "entity_id": "pay_DEXrnipqTmWVGE",
                  "type": "payment",
                  "debit": 0,
                  "credit": 97100,
                  "amount": 100000,
                  "fee": 2900
                }
                """));

        SettlementRecord record = poller.mapPaymentSettlement(settlement);

        assertThat(record.getProviderRefId()).isEqualTo("pay_DEXrnipqTmWVGE");
        assertThat(record.getGrossAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(record.getFeeAmount()).isEqualByComparingTo(new BigDecimal("29.00"));
        assertThat(record.getNetAmount()).isEqualByComparingTo(new BigDecimal("971.00"));
        assertThat(record.getExternalStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void skipsNonPaymentReconItems() {
        Settlement refund = new Settlement(new JSONObject("""
                {"entity_id": "rfnd_DGRcGzZSLyEdg1", "type": "refund", "debit": 242500, "credit": 0, "fee": 0}
                """));

        assertThat(poller.mapPaymentSettlement(refund)).isNull();
    }
}
