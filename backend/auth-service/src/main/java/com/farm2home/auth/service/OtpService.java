package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.OtpVerification;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.repository.OtpVerificationRepository;
import com.farm2home.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // TODO: Integrate with SMS provider (e.g., MSG91, AWS SNS, Twilio)
        log.info("OTP for mobile {} (type: {}): {} [DEVELOPMENT ONLY — remove in production]",
                mobile, otpType, otp);
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
}
