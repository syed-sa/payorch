// File: src/main/java/com/payorch/orchestrator/service/SmartRoutingStrategy.java
package com.payorch.orchestrator.service;

import com.payorch.orchestrator.model.PSPHealth;
import com.payorch.providers.service.PaymentProvider; // Using your interface
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SmartRoutingStrategy {

    // Spring automatically injects all beans implementing PaymentProvider
    // (StripeProvider, RazorpayProvider, etc.)
    private final List<PaymentProvider> providers;
    private final MetricsService metricsService;

    private static final double W_SUCCESS = 0.60;
    private static final double W_COST = 0.30;
    private static final double W_LATENCY = 0.10;

    /**
     * Iterates through all available Provider beans and selects the best one based
     * on
     * weighted health metrics from Redis/DB.
     */
    public PaymentProvider selectBestProvider() {
        return providers.stream()
                .filter(this::isProviderAvailable)
                .max(Comparator.comparingDouble(this::calculateScore))
                .orElseThrow(
                        () -> new RuntimeException("CRITICAL: No healthy payment providers available in the circuit"));
    }

    private boolean isProviderAvailable(PaymentProvider provider) {
        return metricsService.getHealth(provider.getProviderId()).isActive();
    }

    private double calculateScore(PaymentProvider provider) {
        String pspId = provider.getProviderId();
        PSPHealth health = metricsService.getHealth(pspId);

        // Normalized scoring logic
        double successScore = health.getSuccessRate() * W_SUCCESS;
        double costScore = (1.0 - health.getCostBase()) * W_COST;
        double latencyScore = Math.max(0, (2000 - health.getP95Latency()) / 2000.0) * W_LATENCY;

        double totalScore = successScore + costScore + latencyScore;

        log.debug("PSP Score Calculation -> ID: {}, Total: {}, [S: {}, C: {}, L: {}]",
                pspId, totalScore, successScore, costScore, latencyScore);

        return totalScore;
    }

    public PaymentProvider selectBestProviderExcluding(List<String> excludedIds) {
        return providers.stream()
                .filter(p -> !excludedIds.contains(p.getProviderId()))
                .filter(this::isProviderAvailable)
                .max(Comparator.comparingDouble(this::calculateScore))
                .orElseThrow(() -> new RuntimeException("No backup payment providers available for route allocation"));
    }

    public int getAvailableProviderCount() {
        return providers.size();
    }
}