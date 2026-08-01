package com.farm2home.auth.service;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpRepository;
    private final UserRepository userRepository;
    private final OtpEventProducer otpEventProducer;
    private final AuditLogService auditLogService;
    private final SmsService smsService;

    @Transactional
    public void generateAndSend(String mobile, OtpType otpType) {
        // Invalidate any previous unused OTPs for same mobile+type
        otpRepository.markAllUsedByMobileAndType(mobile, otpType);

        String otp = generateOtp();

        OtpVerification record = OtpVerification.builder()
                .mobile(mobile)
                .otp(otp)
                .otpType(otpType)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();

        otpRepository.save(record);

        // SmsService handles retries/failure/audit itself and never throws - a transient SMS
        // outage must never fail OTP generation, since the record above is already persisted
        // and usable (e.g. read back by support) regardless of whether delivery succeeded.
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

    @Transactional
    public void verify(String mobile, String otp, OtpType otpType) {
        OtpVerification record = otpRepository
                .findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(mobile, otpType)
                .orElseThrow(() -> new AuthException("No OTP found for this mobile number. Please request a new OTP."));

        if (record.isExpired()) {
            throw new AuthException("OTP has expired. Please request a new OTP.");
        }

        if (!record.getOtp().equals(otp)) {
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
        return "Your Farm2Home Milk OTP is " + otp + ". Valid for " + OTP_EXPIRY_MINUTES
                + " minutes. Do not share this code with anyone.";
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
