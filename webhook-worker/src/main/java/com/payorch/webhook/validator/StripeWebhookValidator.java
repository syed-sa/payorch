package com.payorch.webhook.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Validator for Stripe webhook signatures.
 * Uses HMAC-SHA256 for signature verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookValidator {

    @Value("${webhook.stripe.secret:}")
    private String stripeWebhookSecret;

    /**
     * Validates the signature of a Stripe webhook
     * 
     * @param payload         The raw request body
     * @param signatureHeader The Stripe-Signature header value
     * @return true if signature is valid, false otherwise
     */
    public boolean isValidSignature(String payload, String signatureHeader) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isEmpty()) {
            log.warn("Stripe webhook secret not configured");
            return false;
        }

        if (signatureHeader == null || signatureHeader.isEmpty()) {
            log.warn("Missing Stripe-Signature header");
            return false;
        }

        try {
            String computedSignature = computeSignature(payload, stripeWebhookSecret);

            // Stripe provides signatures in format: t=timestamp,v1=signature1,v0=signature0
            // We check the v1 signature (HMAC-SHA256)
            String[] parts = signatureHeader.split(",");
            for (String part : parts) {
                if (part.startsWith("v1=")) {
                    String providedSignature = part.substring(3);
                    if (computedSignature.equals(providedSignature)) {
                        log.debug("Stripe webhook signature validation successful");
                        return true;
                    }
                }
            }

            log.warn("Stripe webhook signature validation failed");
            return false;
        } catch (Exception e) {
            log.error("Error validating Stripe webhook signature", e);
            return false;
        }
    }

    /**
     * Computes HMAC-SHA256 signature for the payload
     */
    private String computeSignature(String payload, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(bytes);
    }
}
