package com.payorch.providers.service.impl;

import java.util.Locale;

import com.payorch.shared.providers.dto.ProviderStatus;

public final class ProviderStatusMapper {

    private ProviderStatusMapper() {
    }

    public static ProviderStatus map(String externalStatus) {
        if (externalStatus == null) {
            return ProviderStatus.PENDING;
        }

        String normalized = externalStatus.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "captured":
            case "paid":
            case "succeeded":
                return ProviderStatus.SUCCESS;
            case "created":
            case "processing":
            case "requires_action":
            case "requires_payment_method":
            case "requires_confirmation":
            case "requires_capture":
            case "attempted":
                return ProviderStatus.PENDING;
            case "failed":
            case "canceled":
            case "cancelled":
                return ProviderStatus.FAILED;
            default:
                return ProviderStatus.PENDING;
        }
    }
}
