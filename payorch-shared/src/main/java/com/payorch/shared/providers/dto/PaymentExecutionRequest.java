package com.payorch.shared.providers.dto;

import com.payorch.shared.model.Transaction;

public record PaymentExecutionRequest(
        Transaction transaction,
        String paymentMethodToken) {
}
