// File: core-orchestrator/src/main/java/com/payorch/providers/webhook/parser/RazorpayWebhookParser.java
package com.payorch.webhook.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.shared.providers.dto.NormalizedWebhookData;
import com.payorch.shared.providers.dto.ProviderStatus;
import com.payorch.webhook.WebhookParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayWebhookParser implements WebhookParser {

    private final ObjectMapper objectMapper;

    @Override
    public String getSupportedProvider() {
        return "RAZORPAY";
    }

    @Override
    public NormalizedWebhookData parse(String rawJsonPayload) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawJsonPayload);
            JsonNode paymentEntity = rootNode.path("payload").path("payment").path("entity");
            
            String providerRefId = paymentEntity.path("id").asText();
            String eventType = rootNode.path("event").asText();
            
            ProviderStatus mappedStatus;
            String errorMessage = null;

            if ("payment.captured".equals(eventType)) {
                mappedStatus = ProviderStatus.SUCCESS;
            } else if ("payment.failed".equals(eventType)) {
                mappedStatus = ProviderStatus.FAILED;
                errorMessage = paymentEntity.path("error_description").asText("Razorpay bank terminal processing failure");
            } else {
                mappedStatus = ProviderStatus.PENDING;
            }

            return NormalizedWebhookData.builder()
                    .providerRefId(providerRefId)
                    .status(mappedStatus)
                    .errorMessage(errorMessage)
                    .build();

        } catch (Exception e) {
            log.error("Failed to map incoming Razorpay webhook JSON schema", e);
            throw new IllegalArgumentException("Invalid layout or signatures inside Razorpay JSON object", e);
        }
    }
}