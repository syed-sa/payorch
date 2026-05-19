package com.payorch.webhook.controller;

import com.payorch.webhook.producer.WebhookEventProducer;
import com.payorch.webhook.validator.RazorpayWebhookValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for receiving Razorpay webhooks.
 * Receives, validates, and publishes webhook events to Kafka.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final WebhookEventProducer webhookEventProducer;
    private final RazorpayWebhookValidator razorpayWebhookValidator;

    /**
     * Receives Razorpay webhook events
     * 
     * @param payload         The raw webhook payload
     * @param signatureHeader The X-Razorpay-Signature header
     * @return Immediate acknowledgment to Razorpay
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signatureHeader) {

        try {
            log.info("Received Razorpay webhook event");

            // Validate webhook signature
            if (!razorpayWebhookValidator.isValidSignature(payload, signatureHeader)) {
                log.warn("Invalid Razorpay webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid signature"));
            }

            // Extract event ID from payload for idempotent key
            // Razorpay webhooks have an event field; use UUID for idempotency if not
            // available
            String eventId = extractEventId(payload);
            if (eventId == null) {
                // Generate UUID as fallback for idempotency
                eventId = UUID.randomUUID().toString();
                log.debug("Generated UUID for Razorpay webhook event: {}", eventId);
            }

            // Publish to Kafka immediately
            webhookEventProducer.publishRazorpayWebhook(payload, eventId);

            // Acknowledge receipt back to Razorpay
            Map<String, Object> response = new HashMap<>();
            response.put("status", "received");
            response.put("eventId", eventId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing Razorpay webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process webhook"));
        }
    }

    /**
     * Simple health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "razorpay-webhook"));
    }

    /**
     * Extracts event ID from Razorpay JSON payload
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
            log.debug("Error extracting event ID from Razorpay payload", e);
        }
        return null;
    }
}
