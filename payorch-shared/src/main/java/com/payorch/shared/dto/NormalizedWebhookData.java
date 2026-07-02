package com.payorch.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedWebhookData {
    private String providerRefId;
    private ProviderStatus status;
    private String errorMessage;
}
