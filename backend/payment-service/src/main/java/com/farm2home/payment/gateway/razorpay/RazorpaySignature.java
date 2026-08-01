package com.farm2home.payment.gateway.razorpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signature verification, used both for checkout verification (payload is
 * {@code "{order_id}|{payment_id}"}, keyed with the account's key secret) and webhook validation
 * (payload is the raw request body, keyed with the separate webhook secret) — Razorpay uses the
 * same signing scheme for both, just different payloads and keys.
 */
final class RazorpaySignature {

    private RazorpaySignature() {
    }

    static boolean isValid(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            // Constant-time comparison — a naive String.equals() would leak how many leading
            // hex characters matched via timing, letting an attacker brute-force the signature.
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
