// File: core-orchestrator/src/main/java/com/payorch/providers/webhook/parser/StripeWebhookParser.java
package com.payorch.webhook.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payorch.providers.dto.ProviderStatus;
import com.payorch.webhook.WebhookParser;
import com.payorch.providers.dto.NormalizedWebhookData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripeWebhookParser implements WebhookParser {

    private final ObjectMapper objectMapper;

    @Override
    public String getSupportedProvider() {
        return "STRIPE";
    }

    @Override
    public NormalizedWebhookData parse(String rawJsonPayload) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawJsonPayload);
            JsonNode dataObject = rootNode.path("data").path("object");
            
            String providerRefId = dataObject.path("id").asText();
            String eventType = rootNode.path("type").asText();
            
            ProviderStatus mappedStatus;
            String errorMessage = null;

            if ("payment_intent.succeeded".equals(eventType) || "charge.succeeded".equals(eventType)) {
                mappedStatus = ProviderStatus.SUCCESS;
            } else if ("payment_intent.payment_failed".equals(eventType) || "charge.failed".equals(eventType)) {
                mappedStatus = ProviderStatus.FAILED;
                errorMessage = dataObject.path("last_payment_error").path("message").asText("Stripe core network execution failure");
            } else {
                mappedStatus = ProviderStatus.PENDING;
            }

            return NormalizedWebhookData.builder()
                    .providerRefId(providerRefId)
                    .status(mappedStatus)
                    .errorMessage(errorMessage)
                    .build();

        } catch (Exception e) {
            log.error("Failed to map incoming Stripe webhook JSON schema", e);
            throw new IllegalArgumentException("Invalid layout or signatures inside Stripe JSON object", e);
        }
    }
}