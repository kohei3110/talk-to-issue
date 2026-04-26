package com.github.talktoissue.server;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WebhookValidatorTest {

    private static final String SECRET = "test-webhook-secret";

    private final WebhookValidator validator = new WebhookValidator(SECRET);

    @Test
    void validSignature() {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, SECRET);

        assertTrue(validator.isValid(signature, body));
    }

    @Test
    void invalidSignature() {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);

        assertFalse(validator.isValid("sha256=0000000000000000000000000000000000000000000000000000000000000000", body));
    }

    @Test
    void tamperedBody() {
        byte[] original = "hello world".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(original, SECRET);

        byte[] tampered = "hello TAMPERED".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValid(signature, tampered));
    }

    @Test
    void nullSignatureHeader() {
        byte[] body = "test".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValid(null, body));
    }

    @Test
    void missingPrefix() {
        byte[] body = "test".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValid("abc123", body));
    }

    @Test
    void wrongPrefix() {
        byte[] body = "test".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValid("sha1=abc123", body));
    }

    @Test
    void emptyBody() {
        byte[] body = new byte[0];
        String signature = computeSignature(body, SECRET);

        assertTrue(validator.isValid(signature, body));
    }

    @Test
    void largeBody() {
        byte[] body = new byte[100_000];
        java.util.Arrays.fill(body, (byte) 'A');
        String signature = computeSignature(body, SECRET);

        assertTrue(validator.isValid(signature, body));
    }

    @Test
    void differentSecretFails() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signatureWithOtherSecret = computeSignature(body, "other-secret");

        assertFalse(validator.isValid(signatureWithOtherSecret, body));
    }

    private static String computeSignature(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body);
            var sb = new StringBuilder("sha256=");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
