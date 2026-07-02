package com.payorch.shared.util;

public final class TokenMaskingUtil {

    private TokenMaskingUtil() {
    }

    public static String mask(String token) {
        if (token == null || token.isBlank()) {
            return "****";
        }
        int idx = token.indexOf('_');
        return idx > 0 ? token.substring(0, idx + 1) + "****" : "****";
    }
}
