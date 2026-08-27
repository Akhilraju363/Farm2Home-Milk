package com.farm2home.notification.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code email.*}, itself sourced from environment variables (see EMAIL_ENABLED/
 *  EMAIL_PROVIDER/EMAIL_MAX_ATTEMPTS/EMAIL_RETRY_BACKOFF_MILLIS in .env.example) - mirrors
 *  {@code com.farm2home.common.core.sms.SmsProperties} exactly. SMTP host/port/username/password
 *  stay under {@code spring.mail.*} (Spring's own binding target for {@code JavaMailSender}), and
 *  the from-address stays under {@code app.mail.from} (both pre-existing) - only the properties
 *  genuinely specific to the provider-selection/retry layer live here. */
@Data
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    /** Master switch. False (default) always selects {@link LoggingEmailProvider} regardless of
     *  {@link #provider}, so local dev/CI never attempts a real SMTP connection unless explicitly
     *  opted in - matching {@code sms.provider}/{@code push.provider}'s safe-by-default shape. */
    private boolean enabled = false;

    /** {@code smtp} (only real option today) or a future real provider's own property value;
     *  irrelevant while {@link #enabled} is false. */
    private String provider = "smtp";

    /** Display name used as the email "From" header alongside {@code app.mail.from}'s address. */
    private String fromName = "Farm2Home";

    /** Total send attempts (including the first) before giving up. */
    private int maxAttempts = 3;

    /** Base backoff between attempts, in milliseconds - multiplied by the attempt number (linear
     *  backoff), same shape as {@code SmsProperties.retryBackoffMillis}. */
    private long retryBackoffMillis = 150;
}
