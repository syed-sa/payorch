package com.payorch.shared.providers.service.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.payorch.shared.model.Transaction;
import com.payorch.shared.providers.dto.PaymentExecutionRequest;
import com.payorch.shared.providers.dto.ProviderResponse;
import com.payorch.shared.providers.dto.ProviderStatus;
import com.payorch.shared.providers.dto.ProviderTransactionDetails;
import com.payorch.shared.providers.exception.ProviderStatusException;
import com.payorch.shared.providers.service.PaymentProvider;
import com.payorch.shared.providers.util.TokenMaskingUtil;

import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

@Service
@Slf4j
public class RazorpayProvider implements PaymentProvider {

    @Value("${payment.razorpay.key-id}")
    private String keyId;

    @Value("${payment.razorpay.key-secret}")
    private String keySecret;

    @Override
    public ProviderResponse process(PaymentExecutionRequest request) {
        validatePaymentMethodToken(request.paymentMethodToken());
        Transaction transaction = request.transaction();
        log.info("Razorpay processing transaction {} using payment method token {}",
                transaction.getId(), TokenMaskingUtil.mask(request.paymentMethodToken()));

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            // Razorpay also expects amount in paise (1 INR = 100 Paise)
            orderRequest.put("amount", transaction.getAmount().multiply(new java.math.BigDecimal(100)).longValue());
            orderRequest.put("currency", transaction.getCurrency());
            orderRequest.put("receipt", transaction.getId().toString());

            Order order = client.orders.create(orderRequest);

            return ProviderResponse.builder()
                    .externalId(order.get("id"))
                    .status(ProviderStatus.PENDING)
                    .rawResponse(order.toString())
                    .build();

        } catch (Exception e) {
            log.error("Razorpay payment creation failed for transaction {}", transaction.getId(), e);
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
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            Order order = client.orders.fetch(providerReferenceId);
            String externalStatus = order.get("status");

            return ProviderTransactionDetails.builder()
                    .providerReferenceId(providerReferenceId)
                    .externalStatus(externalStatus)
                    .amount(BigDecimal.valueOf(((Number) order.get("amount")).longValue(), 2))
                    .status(ProviderStatusMapper.map(externalStatus))
                    .rawResponse(order.toString())
                    .fetchedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch Razorpay order status for {}", providerReferenceId, e);
            throw new ProviderStatusException(
                    "Unable to fetch Razorpay status for provider reference " + providerReferenceId, e);
        }
    }

    @Override
    public String getProviderId() {
        return "RAZORPAY";
    }

    @Override
    public boolean supports(String providerId) {
        return "RAZORPAY".equalsIgnoreCase(providerId);
    }
}
