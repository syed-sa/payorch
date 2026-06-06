package com.payorch.providers.service.impl;

import com.payorch.model.Transaction;
import com.payorch.providers.dto.ProviderResponse;
import com.payorch.providers.dto.ProviderStatus;
import com.payorch.providers.dto.ProviderTransactionDetails;
import com.payorch.providers.exception.ProviderStatusException;
import com.payorch.providers.service.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StripeProvider implements PaymentProvider {

    @Value("${payment.stripe.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }

    @Override
    public ProviderResponse process(Transaction transaction) {
        try {
            // Stripe expects amounts in cents (e.g., 10.00 becomes 1000)
            long amountInCents = transaction.getAmount().multiply(new java.math.BigDecimal(100)).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(transaction.getCurrency().toLowerCase())
                    .putMetadata("transaction_id", transaction.getId().toString())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            return ProviderResponse.builder()
                    .externalId(intent.getId())
                    .status(ProviderStatus.PENDING) // Stripe Intent starts as pending
                    .rawResponse(intent.toJson())
                    .build();

        } catch (Exception e) {
            log.error("Stripe payment creation failed for transaction {}", transaction.getId(), e);
            return ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderTransactionDetails fetchStatus(String providerReferenceId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerReferenceId);
            String externalStatus = intent.getStatus();

            return ProviderTransactionDetails.builder()
                    .providerReferenceId(providerReferenceId)
                    .externalStatus(externalStatus)
                    .status(ProviderStatusMapper.map(externalStatus))
                    .rawResponse(intent.toJson())
                    .fetchedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch Stripe payment status for {}", providerReferenceId, e);
            throw new ProviderStatusException(
                    "Unable to fetch Stripe payment status for provider reference " + providerReferenceId, e);
        }
    }

    @Override
    public String getProviderId() {
        return "STRIPE";
    }

    @Override
    public boolean supports(String providerId) {
        return "STRIPE".equalsIgnoreCase(providerId);
    }
}
