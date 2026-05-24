package com.payorch.providers.webhook.consumer;

import com.payorch.orchestrator.service.PaymentStateManager;
import com.payorch.providers.webhook.WebhookParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import com.payorch.providers.dto.NormalizedWebhookData;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventConsumer {

    private final WebhookParserFactory parserFactory;
    private final PaymentStateManager stateManager;

    @KafkaListener(
            topics = "payment-provider-webhooks",
            groupId = "payorch-ledger-update-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeWebhookPayloadStream(
            ConsumerRecord<String, String> record,
            @Header(name = "X-Provider-Type", defaultValue = "STRIPE") String providerType,
            Acknowledgment acknowledgment) {

        log.info("Pulled incoming stream element from partition: {} at log tracker index offset: {}", 
                record.partition(), record.offset());

        try {
            // 1. Structural transformation inside the isolated providers domain boundary
            NormalizedWebhookData normalizedData = parserFactory.getParser(providerType).parse(record.value());

            log.info("Decoded payload footprint. Provider ID: {}, Normalized Status Code: {}", 
                    normalizedData.getProviderRefId(), normalizedData.getStatus());

            // 2. Cross boundary domain function call -> Invoking Orchestrator State Engine to finalize the ledger
            stateManager.processWebhookStateTransition(
                    normalizedData.getProviderRefId(),
                    normalizedData.getStatus().name(),
                    normalizedData.getErrorMessage()
            );

            // 3. Clear commit step
            acknowledgment.acknowledge();
            log.info("Kafka consumer log marker advanced successfully for record offset: {}", record.offset());

        } catch (Exception processingException) {
            log.error("Processing execution failure trapped at consumer boundary for offset: {}", record.offset(), processingException);
            // Retain message within log index loop to force backpressure recovery
            throw processingException;
        }
    }
}   