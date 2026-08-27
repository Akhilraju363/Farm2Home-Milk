package com.farm2home.auth.domain.repository;

import com.farm2home.auth.domain.entity.OtpVerification;
import com.farm2home.auth.domain.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(
            String mobile, OtpType otpType);

    /** Most recent record for (mobile, otpType) regardless of used/expired state - the resend
     *  cooldown (OtpProperties.resendCooldownSeconds) is measured from this record's createdAt,
     *  not from an unused one, since a cooldown must apply even right after the customer just
     *  verified successfully (otherwise "used=false" filtering would make the cooldown a no-op
     *  the moment a code is consumed). */
    Optional<OtpVerification> findTopByMobileAndOtpTypeOrderByCreatedAtDesc(String mobile, OtpType otpType);

    /** Resend-abuse guard: how many OTPs have been generated for (mobile, otpType) since
     *  `since` - see OtpService.generateAndSend()'s OTP_MAX_RESENDS check. */
    long countByMobileAndOtpTypeAndCreatedAtAfter(String mobile, OtpType otpType, LocalDateTime since);

    @Modifying
    @Query("UPDATE OtpVerification o SET o.used = true WHERE o.mobile = :mobile AND o.otpType = :otpType AND o.used = false")
    void markAllUsedByMobileAndType(String mobile, OtpType otpType);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :before")
    void deleteExpiredOtps(LocalDateTime before);
}
