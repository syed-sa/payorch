package com.payorch.providers.service.impl;

import com.payorch.shared.model.Transaction;
import com.payorch.shared.providers.dto.PaymentExecutionRequest;
import com.payorch.shared.providers.dto.ProviderResponse;
import com.payorch.shared.providers.dto.ProviderStatus;
import com.payorch.shared.providers.dto.ProviderTransactionDetails;
import com.payorch.shared.providers.exception.ProviderStatusException;
import com.payorch.shared.providers.service.PaymentProvider;
import com.payorch.shared.providers.util.TokenMaskingUtil;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

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
    public ProviderResponse process(PaymentExecutionRequest request) {
        validatePaymentMethodToken(request.paymentMethodToken());
        Transaction transaction = request.transaction();
        log.info("Stripe processing transaction {} using payment method token {}",
                transaction.getId(), TokenMaskingUtil.mask(request.paymentMethodToken()));

        try {
            long amountInCents = transaction.getAmount().multiply(new java.math.BigDecimal(100)).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(transaction.getCurrency().toLowerCase())
                    .setPaymentMethod(request.paymentMethodToken())
                    .putMetadata("transaction_id", transaction.getId().toString())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            return ProviderResponse.builder()
                    .externalId(intent.getId())
                    .status(ProviderStatus.PENDING)
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

    private void validatePaymentMethodToken(String paymentMethodToken) {
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("Payment method token is mandatory");
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
                    .amount(BigDecimal.valueOf(intent.getAmount(), 2))
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
