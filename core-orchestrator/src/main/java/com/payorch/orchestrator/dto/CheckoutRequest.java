// File: core-orchestrator/src/main/java/com/payorch/orchestrator/dto/CheckoutRequest.java
package com.payorch.orchestrator.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CheckoutRequest {
    private String ownerId;
    private BigDecimal amount;
    private String currency;
}