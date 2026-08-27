package com.farm2home.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code otp.*}, sourced from OTP_EXPIRY_SECONDS/OTP_MAX_ATTEMPTS/
 *  OTP_RESEND_COOLDOWN_SECONDS/OTP_MAX_RESENDS (see .env.example) - mirrors the shape of
 *  {@code SmsProperties}/{@code EmailProperties} from the same codebase. OTP length itself is
 *  deliberately NOT made configurable here: {@code ValidationConstants.OTP_LENGTH} (=6) is
 *  already a shared, compile-time constant baked into {@code VerifyOtpRequest}'s
 *  {@code @Size}/{@code @Pattern} validation - making generation length configurable without also
 *  making that annotation-based validation dynamic would let the two drift out of sync, so this
 *  keeps the project's existing established policy rather than blindly adding OTP_LENGTH per the
 *  task's own example (which explicitly allows deviating from its example values). */
@Data
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    /** How long a generated OTP remains valid. */
    private int expirySeconds = 300;

    /** Verification attempts allowed against one generated OTP before it's burned (marked used)
     *  even though it was never successfully verified. */
    private int maxAttempts = 5;

    /** Minimum time between two generateAndSend() calls for the same (mobile, otpType). */
    private int resendCooldownSeconds = 30;

    /** Maximum OTPs that may be generated for the same (mobile, otpType) within the resend
     *  window (see OtpService.RESEND_WINDOW) before generateAndSend() refuses further requests. */
    private int maxResends = 3;
}
