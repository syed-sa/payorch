package com.payorch.shared.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProviderTransactionDetails {
    private String providerReferenceId;
    private String externalStatus;
    private BigDecimal amount;
    private String rawResponse;
    private LocalDateTime fetchedAt;
    private ProviderStatus status;
}
