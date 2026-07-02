package com.payorch.providers.service.impl;

import com.payorch.shared.contract.PaymentProvider;
import com.payorch.shared.dto.PaymentExecutionRequest;
import com.payorch.shared.dto.ProviderResponse;
import com.payorch.shared.dto.ProviderStatus;
import com.payorch.shared.dto.ProviderTransactionDetails;
import com.payorch.shared.model.Transaction;
import com.payorch.shared.util.TokenMaskingUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simulates provider behavior for scenarios that cannot be reproduced against real sandboxes.
 * This is intended for manual exploration and should not be used by automated tests.
 */
@Service
@Profile("demo")
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResponse process(PaymentExecutionRequest request) {
        validatePaymentMethodToken(request.paymentMethodToken());
        Transaction transaction = request.transaction();
        log.info("Mock Provider processing transaction {} using payment method token {}",
                transaction.getId(), TokenMaskingUtil.mask(request.paymentMethodToken()));

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

    private void validatePaymentMethodToken(String paymentMethodToken) {
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("Payment method token is mandatory");
        }
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
