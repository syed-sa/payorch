package com.payorch.providers.dto;

import com.payorch.providers.dto.ProviderStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ProviderTransactionDetails {
    private String providerReferenceId;
    private String externalStatus;
    private String rawResponse;
    private LocalDateTime fetchedAt;
    private ProviderStatus status;
}
