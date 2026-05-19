package com.payorch.outbox.service.impl;

import com.payorch.outbox.model.OutboxEvent;
import com.payorch.outbox.service.MessagePublisher;
import com.payorch.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPatternWorker {

    private final OutboxRepository outboxRepository;
    private final MessagePublisher messagePublisher;

    private static final int BATCH_SIZE = 50;
    private static final String PAYMENT_EVENTS_TOPIC = "payment-lifecycle-events";

    /**
     * Polls the outbox table every 500 milliseconds.
     * Uses fixedDelay to ensure that the next execution execution loop doesn't start 
     * until the previous batch has fully finished.
     */
    @Scheduled(fixedDelay = 500)
    public void pollAndPublishOutboxEvents() {
        // We call a separate transactional method to limit the scope of the DB lock execution lifespan
        List<OutboxEvent> pendingEvents = fetchAndLockBatch();
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Outbox worker picked up {} events for processing.", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // 1. Publish payload securely to Kafka message broker
                // We use the Outbox ID as the key to preserve regional execution order in Kafka partitions
                messagePublisher.publish(PAYMENT_EVENTS_TOPIC, event.getId().toString(), event.getPayload());

                // 2. Mark event as completed in database
                updateEventStatus(event, "PROCESSED");

            } catch (Exception brokerException) {
                log.error("Failed to stream outbox event: {}. Retrying on next batch run.", event.getId(), brokerException);
                updateEventStatus(event, "FAILED");
                // Note: We don't break the loop here so a single bad event doesn't block the remaining batch elements
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> fetchAndLockBatch() {
        return outboxRepository.findPendingEventsForProcessing(PageRequest.of(0, BATCH_SIZE));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateEventStatus(OutboxEvent event, String targetStatus) {
        outboxRepository.findById(event.getId()).ifPresent(record -> {
            record.setStatus(targetStatus);
            outboxRepository.saveAndFlush(record);
        });
    }
}