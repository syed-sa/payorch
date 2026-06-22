package com.payorch.shared.providers.service.impl;

import com.payorch.shared.model.Transaction;
import com.payorch.shared.providers.dto.PaymentExecutionRequest;
import com.payorch.shared.providers.dto.ProviderResponse;
import com.payorch.shared.providers.dto.ProviderStatus;
import com.payorch.shared.providers.dto.ProviderTransactionDetails;
import com.payorch.shared.providers.service.PaymentProvider;
import com.payorch.shared.providers.util.TokenMaskingUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
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
