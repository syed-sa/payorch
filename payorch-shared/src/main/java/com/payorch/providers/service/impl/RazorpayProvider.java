package com.payorch.providers.service.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.payorch.ledger.model.Transaction;
import com.payorch.providers.dto.ProviderResponse;
import com.payorch.providers.dto.ProviderStatus;
import com.payorch.providers.dto.ProviderTransactionDetails;
import com.payorch.providers.exception.ProviderStatusException;
import com.payorch.providers.service.PaymentProvider;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RazorpayProvider implements PaymentProvider {

    @Value("${payment.razorpay.key-id}")
    private String keyId;

    @Value("${payment.razorpay.key-secret}")
    private String keySecret;

    @Override
    public ProviderResponse process(Transaction transaction) {
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

    @Override
    public ProviderTransactionDetails fetchStatus(String providerReferenceId) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            Order order = client.orders.fetch(providerReferenceId);
            String externalStatus = order.get("status");

            return ProviderTransactionDetails.builder()
                    .providerReferenceId(providerReferenceId)
                    .externalStatus(externalStatus)
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
