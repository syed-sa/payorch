// File: src/main/java/com/payorch/outbox/messaging/MessagePublisher.java
package com.payorch.outbox.service;

public interface MessagePublisher {
    void publish(String topic, String key, String payload);
}

