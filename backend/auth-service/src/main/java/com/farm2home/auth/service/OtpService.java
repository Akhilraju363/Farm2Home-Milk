package com.farm2home.auth.service;

import com.farm2home.auth.config.OtpProperties;
import com.farm2home.auth.domain.entity.OtpVerification;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.repository.OtpVerificationRepository;
import com.farm2home.auth.domain.repository.UserRepository;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.kafka.OtpEventProducer;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    /** Window the resend-abuse guard (OtpProperties.maxResends) counts within - an
     *  implementation constant, not user-configurable, since it just needs to be "long enough to
     *  make a counted limit meaningful" rather than a tunable business policy like the values in
     *  OtpProperties actually are. */
    private static final int RESEND_WINDOW_MINUTES = 60;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpRepository;
    private final UserRepository userRepository;
    private final OtpEventProducer otpEventProducer;
    private final AuditLogService auditLogService;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final OtpProperties otpProperties;

    @Transactional
    public void generateAndSend(String mobile, OtpType otpType) {
        enforceResendLimits(mobile, otpType);

        // Invalidate any previous unused OTPs for same mobile+type
        otpRepository.markAllUsedByMobileAndType(mobile, otpType);

        String otp = generateOtp();

        OtpVerification record = OtpVerification.builder()
                .mobile(mobile)
                .otp(passwordEncoder.encode(otp))
                .otpType(otpType)
                .used(false)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusSeconds(otpProperties.getExpirySeconds()))
                .build();

        otpRepository.save(record);

        // SmsService handles retries/failure/audit itself and never throws - a transient SMS
        // outage must never fail OTP generation, since the record above is already persisted
        // and usable (e.g. read back by support) regardless of whether delivery succeeded.
        // `otp` (the raw code) lives only in this method's local scope - never persisted,
        // returned, or logged beyond this point.
        smsService.sendSms(mobile, buildOtpMessage(otp), EmailTemplateConstants.EVENT_OTP);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.OTP_SENT)
                .entityType("Otp")
                .entityId(record.getId() != null ? record.getId().toString() : mobile)
                .username(mobile)
                .details("OTP sent for " + otpType + " to " + mobile)
                .build());

        // Email is a best-effort second channel - only fires when the mobile already
        // matches a registered user with an email on file (e.g. resend/login flows;
        // during registration itself the user row already exists by this point too).
        userRepository.findByMobileAndDeletedFalse(mobile)
                .filter(user -> user.getEmail() != null)
                .ifPresent(user -> otpEventProducer.publishOtpGenerated(user, otp));
    }

    /** Resend cooldown (can't request again too soon) + resend cap (can't request more than N
     *  times within the rolling window) - neither existed before this change, which let a caller
     *  hit /send-otp an unlimited number of times for the same mobile+type. Both are checked
     *  before any new OTP is generated/sent, and neither reveals to the caller whether the
     *  underlying account exists - only that they must wait or have hit the limit, exactly like
     *  the existing enumeration-safe behavior in AuthServiceImpl.sendOtp(). */
    private void enforceResendLimits(String mobile, OtpType otpType) {
        otpRepository.findTopByMobileAndOtpTypeOrderByCreatedAtDesc(mobile, otpType)
                .ifPresent(last -> {
                    LocalDateTime earliestNextSend = last.getCreatedAt().plusSeconds(otpProperties.getResendCooldownSeconds());
                    if (LocalDateTime.now().isBefore(earliestNextSend)) {
                        throw new AuthException("Please wait before requesting another OTP.");
                    }
                });

        long recentCount = otpRepository.countByMobileAndOtpTypeAndCreatedAtAfter(
                mobile, otpType, LocalDateTime.now().minusMinutes(RESEND_WINDOW_MINUTES));
        if (recentCount >= otpProperties.getMaxResends()) {
            throw new AuthException("Too many OTP requests. Please try again later.");
        }
    }

    // noRollbackFor is essential here, not cosmetic: a wrong-guess attempt both increments
    // record.attempts (via save()) AND throws AuthException to signal failure to the caller.
    // Spring's default behavior rolls back the whole transaction on any unchecked exception,
    // which would silently discard that increment every single time - live-verified to actually
    // happen (see AUTH_SOCIAL_OTP_PROGRESS.md) before this annotation was added, completely
    // defeating the attempt limit (a caller could retry the same OTP indefinitely, since the
    // persisted attempts count would never move past 0). Scoped to this one method only - AuthException
    // thrown from login()/register() elsewhere in this codebase still rolls back normally, which
    // is the correct behavior there.
    @Transactional(noRollbackFor = AuthException.class)
    public void verify(String mobile, String otp, OtpType otpType) {
        OtpVerification record = otpRepository
                .findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(mobile, otpType)
                .orElseThrow(() -> new AuthException("No OTP found for this mobile number. Please request a new OTP."));

        if (record.isExpired()) {
            throw new AuthException("OTP has expired. Please request a new OTP.");
        }

        if (record.getAttempts() >= otpProperties.getMaxAttempts()) {
            // Burn the record even though it was never matched - an attacker who has exhausted
            // their guesses against this code must request a brand new one, not keep trying
            // against the same still-unexpired record.
            record.setUsed(true);
            otpRepository.save(record);
            throw new AuthException("Too many incorrect attempts. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(otp, record.getOtp())) {
            record.setAttempts(record.getAttempts() + 1);
            otpRepository.save(record);
            throw new AuthException("Invalid OTP. Please check and try again.");
        }

        record.setUsed(true);
        otpRepository.save(record);
    }

    private String generateOtp() {
        int otp = 100_000 + SECURE_RANDOM.nextInt(900_000);
        return String.valueOf(otp);
    }

    private String buildOtpMessage(String otp) {
        return "Your Farm2Home Milk OTP is " + otp + ". Valid for "
                + (otpProperties.getExpirySeconds() / 60) + " minutes. Do not share this code with anyone.";
    }

    // ── Scheduled jobs ──────────────────────────────────────────────────────────

    /** Every 10 minutes — purge OTP records past their expiry so the table doesn't grow
     *  unbounded (expiry is otherwise only ever checked lazily, at verify() time). */
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void purgeExpiredOtps() {
        otpRepository.deleteExpiredOtps(LocalDateTime.now());
    }
}
