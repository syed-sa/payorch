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
 * Validator for Razorpay webhook signatures.
 * Uses HMAC-SHA256 for signature verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookValidator {

    @Value("${webhook.razorpay.secret:}")
    private String razorpayWebhookSecret;

    /**
     * Validates the signature of a Razorpay webhook
     * 
     * @param payload         The raw request body
     * @param signatureHeader The X-Razorpay-Signature header value
     * @return true if signature is valid, false otherwise
     */
    public boolean isValidSignature(String payload, String signatureHeader) {
        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isEmpty()) {
            log.warn("Razorpay webhook secret not configured");
            return false;
        }

        if (signatureHeader == null || signatureHeader.isEmpty()) {
            log.warn("Missing X-Razorpay-Signature header");
            return false;
        }

        try {
            String computedSignature = computeSignature(payload, razorpayWebhookSecret);

            if (computedSignature.equals(signatureHeader)) {
                log.debug("Razorpay webhook signature validation successful");
                return true;
            }

            log.warn("Razorpay webhook signature validation failed");
            return false;
        } catch (Exception e) {
            log.error("Error validating Razorpay webhook signature", e);
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
