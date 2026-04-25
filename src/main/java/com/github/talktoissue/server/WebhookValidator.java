package com.github.talktoissue.server;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates GitHub webhook signatures using HMAC-SHA256.
 */
public class WebhookValidator {

    private final byte[] secretBytes;

    public WebhookValidator(String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Validate that the provided signature matches the body using HMAC-SHA256.
     *
     * @param signatureHeader the X-Hub-Signature-256 header value (e.g. "sha256=abc...")
     * @param body            the raw request body
     * @return true if the signature is valid
     */
    public boolean isValid(String signatureHeader, byte[] body) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            String expectedHex = "sha256=" + bytesToHex(expected);
            return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
