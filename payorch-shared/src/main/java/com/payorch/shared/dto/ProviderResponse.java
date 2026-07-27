package com.payorch.shared.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderResponse {
    private String externalId;
    private ProviderStatus status;
    private String rawResponse;
    private String errorMessage;
    private Boolean finalResponse;

    public boolean isSuccess() {
        return ProviderStatus.SUCCESS.equals(this.status);
    }

    public boolean isFinalResponse() {
        if (ProviderStatus.SUCCESS.equals(this.status) || ProviderStatus.PENDING.equals(this.status)) {
            return true;
        }
        return Boolean.TRUE.equals(this.finalResponse);
    }

    public boolean isTransientFailure() {
        return ProviderStatus.FAILED.equals(this.status) && !Boolean.TRUE.equals(this.finalResponse);
    }
}
