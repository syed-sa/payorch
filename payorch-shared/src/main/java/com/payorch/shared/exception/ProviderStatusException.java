package com.payorch.shared.exception;

public class ProviderStatusException extends RuntimeException {
    public ProviderStatusException(String message) {
        super(message);
    }

    public ProviderStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
