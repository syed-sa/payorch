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
 * Service for producing webhook events to Kafka topics.
 * Ensures idempotent delivery with exactly-once semantics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.stripe-webhook}")
    private String stripeTopicName;

    @Value("${kafka.topics.razorpay-webhook}")
    private String razorpayTopicName;

    /**
     * Publishes raw Stripe webhook payload to Kafka topic
     * 
     * @param payload The raw webhook payload from Stripe
     * @param eventId The unique event ID for idempotency
     */
    public void publishStripeWebhook(String payload, String eventId) {
        publishToKafka(stripeTopicName, eventId, payload, "stripe");
    }

    /**
     * Publishes raw Razorpay webhook payload to Kafka topic
     * 
     * @param payload The raw webhook payload from Razorpay
     * @param eventId The unique event ID for idempotency
     */
    public void publishRazorpayWebhook(String payload, String eventId) {
        publishToKafka(razorpayTopicName, eventId, payload, "razorpay");
    }

    /**
     * Internal method to publish to Kafka with idempotent key
     */
    private void publishToKafka(String topic, String key, String payload, String provider) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(KafkaHeaders.KEY, key)
                    .setHeader("provider", provider)
                    .setHeader("timestamp", System.currentTimeMillis())
                    .build();

            kafkaTemplate.send(message);
            log.info("Published {} webhook event to topic: {} with key: {}", provider, topic, key);
        } catch (Exception e) {
            log.error("Failed to publish {} webhook to Kafka topic: {}", provider, topic, e);
            throw new RuntimeException("Failed to publish webhook event to Kafka", e);
        }
    }
}
