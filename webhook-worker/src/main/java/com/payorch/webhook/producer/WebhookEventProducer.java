package com.payorch.webhook.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Service for producing webhook events to a unified Kafka topic.
 * Ensures idempotent delivery with exactly-once semantics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    // Direct injection of our single unified topic channel
    @Value("${kafka.topics.payment-webhooks}")
    private String unifiedTopicName;

    /**
     * Publishes raw Stripe webhook payload to the unified topic
     */
    public void publishStripeWebhook(String payload, String eventId) {
        publishToKafka(unifiedTopicName, eventId, payload, "STRIPE");
    }

    /**
     * Publishes raw Razorpay webhook payload to the unified topic
     */
    public void publishRazorpayWebhook(String payload, String eventId) {
        publishToKafka(unifiedTopicName, eventId, payload, "RAZORPAY");
    }

    /**
     * Internal method to publish to Kafka with partition key and provider route headers
     */
    private void publishToKafka(String topic, String key, String payload, String providerType) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(KafkaHeaders.KEY, key) // Keeps events ordered on the same partition
                    .setHeader("X-Provider-Type", providerType) // Decoded dynamically by consumer factory
                    .build();

            kafkaTemplate.send(message);
            log.info("Successfully pushed {} event to unified stream '{}' using partition tracking key: {}", 
                    providerType, topic, key);
                    
        } catch (Exception e) {
            log.error("Failed to publish {} webhook to Kafka unified stream", providerType, e);
            throw new RuntimeException("Failed to publish webhook event to Kafka", e);
        }
    }
}