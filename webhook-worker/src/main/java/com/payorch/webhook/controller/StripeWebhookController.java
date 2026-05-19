package com.payorch.webhook.controller;

import com.payorch.webhook.producer.WebhookEventProducer;
import com.payorch.webhook.validator.StripeWebhookValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for receiving Stripe webhooks.
 * Receives, validates, and publishes webhook events to Kafka.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final WebhookEventProducer webhookEventProducer;
    private final StripeWebhookValidator stripeWebhookValidator;

    /**
     * Receives Stripe webhook events
     * 
     * @param payload         The raw webhook payload
     * @param signatureHeader The Stripe-Signature header
     * @return Immediate acknowledgment to Stripe
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {

        try {
            log.info("Received Stripe webhook event");

            // Validate webhook signature
            if (!stripeWebhookValidator.isValidSignature(payload, signatureHeader)) {
                log.warn("Invalid Stripe webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid signature"));
            }

            // Extract event ID from payload for idempotent key
            // Format: {"id":"evt_1234...", ...}
            String eventId = extractEventId(payload);
            if (eventId == null) {
                log.warn("Could not extract event ID from Stripe webhook");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid payload"));
            }

            // Publish to Kafka immediately
            webhookEventProducer.publishStripeWebhook(payload, eventId);

            // Acknowledge receipt back to Stripe
            Map<String, Object> response = new HashMap<>();
            response.put("status", "received");
            response.put("eventId", eventId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process webhook"));
        }
    }

    /**
     * Simple health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "stripe-webhook"));
    }

    /**
     * Extracts event ID from Stripe JSON payload
     * This is a simple extraction; for production, use proper JSON parsing
     */
    private String extractEventId(String payload) {
        try {
            int idIndex = payload.indexOf("\"id\":");
            if (idIndex != -1) {
                int startQuote = payload.indexOf("\"", idIndex + 5);
                int endQuote = payload.indexOf("\"", startQuote + 1);
                if (startQuote != -1 && endQuote != -1) {
                    return payload.substring(startQuote + 1, endQuote);
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting event ID from Stripe payload", e);
        }
        return null;
    }
}
