// File: core-orchestrator/src/main/java/com/payorch/orchestrator/controller/CheckoutController.java
package com.payorch.orchestrator.controller;

import com.payorch.orchestrator.dto.CheckoutRequest;
import com.payorch.orchestrator.service.PaymentOrchestrator;
import com.payorch.shared.model.Transaction;
import com.payorch.shared.providers.dto.ProviderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final PaymentOrchestrator paymentOrchestrator;

    @PostMapping("/charge")
    public ResponseEntity<ProviderResponse> executeCharge(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody CheckoutRequest request) {

        log.info("API front-door interceptor activated. Key: {}, Amount: {} {}", 
                idempotencyKey, request.getAmount(), request.getCurrency());

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ProviderResponse.builder()
                            .errorMessage("Mandatory tracking header missing: X-Idempotency-Key")
                            .build());
        }

        // Map the payload directly into your Transaction entity
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setMerchantId(request.getMerchantId());
        transaction.setCustomerReference(request.getCustomerReference());

        // Hand control to your robust orchestration layer logic
        ProviderResponse response = paymentOrchestrator.processPayment(transaction);

        if (response.getStatus() == com.payorch.shared.providers.dto.ProviderStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}