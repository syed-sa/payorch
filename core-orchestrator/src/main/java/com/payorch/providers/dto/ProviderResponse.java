// File: src/main/java/com/payorch/providers/dto/ProviderResponse.java
package com.payorch.providers.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderResponse {
    private String externalId;
    private ProviderStatus status;
    private String rawResponse;
    private String errorMessage;

    // Helper method to resolve the undefined method error
    public boolean isSuccess() {
        return ProviderStatus.SUCCESS.equals(this.status);
    }
}