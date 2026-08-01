package com.farm2home.common.core.push;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code push.*}, itself sourced from environment variables in every consuming
 *  service's {@code application*.yml} (see {@code PUSH_PROVIDER} in {@code .env.example}) -
 *  mirrors {@code SmsProperties} exactly. No provider credentials live in this class itself; a
 *  real provider implementation defines its own {@code @ConfigurationProperties} for whatever API
 *  keys it needs, following the same pattern as payment-service's {@code RazorpayProperties}. */
@Data
@ConfigurationProperties(prefix = "push")
public class PushProperties {

    /** {@code logging} (default) or a future real provider's own property value. */
    private String provider = "logging";

    /** Total send attempts (including the first) before giving up. */
    private int maxAttempts = 3;

    /** Base backoff between attempts, in milliseconds - multiplied by the attempt number
     *  (linear backoff), so retry N waits {@code retryBackoffMillis * N} before the next try. */
    private long retryBackoffMillis = 150;
}
