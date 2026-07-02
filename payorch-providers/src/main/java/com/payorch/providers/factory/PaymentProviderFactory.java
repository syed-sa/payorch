package com.payorch.providers.factory;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.payorch.shared.service.PaymentProvider;

@Component
@RequiredArgsConstructor
public class PaymentProviderFactory {

    private final List<PaymentProvider> providers;

    public PaymentProvider get(String providerId) {
        return providers.stream()
                .filter(p -> p.supports(providerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported provider: " + providerId));
    }
}
