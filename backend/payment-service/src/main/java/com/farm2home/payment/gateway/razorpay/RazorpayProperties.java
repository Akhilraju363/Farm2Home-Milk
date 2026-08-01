package com.farm2home.payment.gateway.razorpay;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code payment.gateway.razorpay.*}, itself sourced from environment variables in
 * every {@code application*.yml} profile (see {@code RAZORPAY_KEY_ID}/{@code RAZORPAY_KEY_SECRET}/
 * {@code RAZORPAY_WEBHOOK_SECRET} in {@code .env.example}) — no key material is ever committed.
 *
 * The {@code key-secret}/{@code webhook-secret} property names deliberately contain "secret",
 * matching Spring Boot Actuator's default {@code Sanitizer} key patterns: if {@code env} is ever
 * exposed via {@code management.endpoints.web.exposure.include} (as it is in this service's
 * application.yml, for legitimate diagnostics), these two values are automatically masked as
 * {@code ******} rather than printed in the clear. {@code @ToString.Exclude} covers the same two
 * fields for direct logging/toString calls that bypass Actuator entirely.
 */
@Data
@ConfigurationProperties(prefix = "payment.gateway.razorpay")
public class RazorpayProperties {

    private String keyId = "";

    @ToString.Exclude
    private String keySecret = "";

    @ToString.Exclude
    private String webhookSecret = "";

    private String baseUrl = "https://api.razorpay.com/v1";

    private String currency = "INR";
}
