// Package: com.payorch.providers.model
package com.payorch.providers.model;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderResponse {
    private String externalId; // The ID returned by Stripe/Razorpay
    private ProviderStatus status; // Our internal normalized status
    private String rawResponse; // Stored for debugging/audit
    private String errorMessage; // Logged if failed
}