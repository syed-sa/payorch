package com.payorch.shared.dto;

import com.payorch.shared.model.Transaction;

public record PaymentExecutionRequest(
        Transaction transaction,
        String paymentMethodToken) {
}
