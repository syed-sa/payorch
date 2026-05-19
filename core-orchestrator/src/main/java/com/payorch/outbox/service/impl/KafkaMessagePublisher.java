package com.payorch.outbox.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.payorch.outbox.service.MessagePublisher;

@Component
@Slf4j
public class KafkaMessagePublisher implements MessagePublisher {
    
    @Override
    public void publish(String topic, String key, String payload) {
        // Simulate an external Kafka broker network call
        log.info("Successfully pushed event to Kafka -> Topic: {}, Partition Key: {}", topic, key);
    }
}