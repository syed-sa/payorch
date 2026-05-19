package com.payorch.orchestrator.model;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PSPHealth {
    private String providerId;
    private double successRate; // Derived from provider_health.last_success_rate
    private long p95Latency;    // Performance metric
    private double costBase;    // Business logic metric
    private boolean isActive;   // From provider_configs.is_active
}