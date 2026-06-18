package com.payorch.orchestrator.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PSPHealth {
    private String providerId;
    private double successRate; // Derived from real-time metrics or fallback defaults
    private long p95Latency; // Performance metric
    private double costBase; // Business logic metric
    private boolean isActive; // Service availability flag
}