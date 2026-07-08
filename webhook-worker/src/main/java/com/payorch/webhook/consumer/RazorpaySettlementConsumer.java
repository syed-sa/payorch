package com.payorch.webhook.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.shared.model.SettlementRecord;
import com.payorch.webhook.repository.SettlementRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class RazorpaySettlementConsumer {

    private final SettlementRecordRepository settlementRecordRepository;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.payment-webhooks}")
    private String razorpayTopic;

    @KafkaListener(topics = "${kafka.topics.payment-webhooks}", groupId = "webhook-settlement-processors")
    public void consume(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("event").asText();

            if (!"order.paid".equals(eventType) && !"payment.captured".equals(eventType)) {
                return;
            }

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
                log.warn("Razorpay settlement payload missing payment entity: {}", payload);
                return;
            }

            String providerRefId = paymentEntity.path("id").asText();
            if (providerRefId == null || providerRefId.isBlank()) {
                log.warn("Razorpay settlement payload missing payment id: {}", payload);
                return;
            }

            BigDecimal grossAmount = toDecimal(paymentEntity.path("amount"));
            BigDecimal feeAmount = toDecimal(paymentEntity.path("fee"));
            BigDecimal netAmount = grossAmount.subtract(feeAmount);

            SettlementRecord settlementRecord = SettlementRecord.builder()
                    .providerRefId(providerRefId)
                    .grossAmount(grossAmount)
                    .feeAmount(feeAmount)
                    .netAmount(netAmount)
                    .externalStatus("SUCCESS")
                    .build();

            // Check if a record already exists for this transaction
            Optional<SettlementRecord> existingRecord = settlementRecordRepository.findByProviderRefId(providerRefId);

            if (existingRecord.isPresent()) {
                log.info("Settlement record already exists for provider reference {}. Updating metadata if required.",
                        providerRefId);
                SettlementRecord recordToUpdate = existingRecord.get();
                recordToUpdate.setGrossAmount(grossAmount);
                recordToUpdate.setFeeAmount(feeAmount);
                recordToUpdate.setNetAmount(netAmount);
                settlementRecordRepository.save(recordToUpdate);
            } else {
                settlementRecordRepository.save(settlementRecord);
                log.info("Persisted new Razorpay settlement record for provider reference {}", providerRefId);
            }
            log.info("Persisted Razorpay settlement record for provider reference {}", providerRefId);
        } catch (Exception e) {
            log.error("Failed to process Razorpay settlement message", e);
        }
    }

    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(node.asLong()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
