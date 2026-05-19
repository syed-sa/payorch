// File: src/main/java/com/payorch/orchestrator/service/MetricsService.java
package com.payorch.orchestrator.service;

import com.payorch.orchestrator.model.PSPHealth;
import com.payorch.providers.repository.ProviderHealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final StringRedisTemplate redisTemplate;
    private final ProviderHealthRepository healthRepository;

    public PSPHealth getHealth(String pspId) {
        // 1. Try Redis for real-time metrics
        String sr = redisTemplate.opsForValue().get("metrics:" + pspId + ":sr");
        String lat = redisTemplate.opsForValue().get("metrics:" + pspId + ":p95");

        if (sr != null && lat != null) {
            return PSPHealth.builder()
                    .providerId(pspId)
                    .successRate(Double.parseDouble(sr))
                    .p95Latency(Long.parseLong(lat))
                    .costBase(calculateCostBase(pspId))
                    .isActive(true)
                    .build();
        }

        // 2. Fallback to DB provider_health table
        return healthRepository.findById(pspId)
                .map(db -> PSPHealth.builder()
                        .providerId(pspId)
                        .successRate(db.getLastSuccessRate().doubleValue() / 100.0)
                        .isActive(db.getIsActive())
                        .p95Latency(250L) // Baseline
                        .costBase(calculateCostBase(pspId))
                        .build())
                .orElse(getDefaultHealth(pspId));
    }

    private double calculateCostBase(String pspId) {
        return pspId.equalsIgnoreCase("STRIPE") ? 0.029 : 0.020;
    }

    private PSPHealth getDefaultHealth(String pspId) {
        return PSPHealth.builder()
                .providerId(pspId)
                .successRate(0.90)
                .isActive(true)
                .p95Latency(300L)
                .costBase(0.025)
                .build();
    }
}