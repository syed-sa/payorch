// Package: com.payorch.providers.service
package com.payorch.providers.service;

import com.payorch.ledger.model.Transaction;
import com.payorch.providers.dto.ProviderResponse;

public interface PaymentProvider {
    
    // Primary method to initiate a payment with a PSP
    ProviderResponse process(Transaction transaction);
    
    // Returns the unique ID of the provider (e.g., "STRIPE", "RAZORPAY")
    String getProviderId();
    
    // Used to check if the provider is healthy/active
    boolean supports(String providerId);
}