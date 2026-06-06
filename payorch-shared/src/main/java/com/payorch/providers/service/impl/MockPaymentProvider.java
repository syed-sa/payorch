// Package: com.payorch.providers.mock
package com.payorch.providers.service.impl;

import com.payorch.model.Transaction;
import com.payorch.providers.dto.ProviderResponse;
import com.payorch.providers.dto.ProviderStatus;
import com.payorch.providers.dto.ProviderTransactionDetails;
import com.payorch.providers.service.PaymentProvider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResponse process(Transaction transaction) {
        log.info("Mock Provider processing transaction: {}", transaction.getId());

        // Simulate a tiny network delay
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
        }

        // Logic: If amount is exactly 999.99, simulate a failure for testing
        if (transaction.getAmount().doubleValue() == 999.99) {
            return ProviderResponse.builder()
                    .status(ProviderStatus.FAILED)
                    .errorMessage("Simulated Mock Failure")
                    .build();
        }

        return ProviderResponse.builder()
                .externalId("MOCK_REF_" + UUID.randomUUID().toString().substring(0, 8))
                .status(ProviderStatus.SUCCESS)
                .rawResponse("{\"message\": \"Mock success\"}")
                .build();
    }

    @Override
    public ProviderTransactionDetails fetchStatus(String providerReferenceId) {
        log.info("Mock provider fetchStatus called for {}", providerReferenceId);
        return ProviderTransactionDetails.builder()
                .providerReferenceId(providerReferenceId)
                .externalStatus("mocked")
                .status(ProviderStatus.PENDING)
                .rawResponse("{\"message\": \"Mock status lookup not implemented\"}")
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public String getProviderId() {
        return "MOCK";
    }

    @Override
    public boolean supports(String providerId) {
        return "MOCK".equalsIgnoreCase(providerId);
    }
}
