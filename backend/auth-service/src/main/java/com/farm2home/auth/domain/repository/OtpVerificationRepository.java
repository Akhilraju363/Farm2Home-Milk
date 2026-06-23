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

    @Modifying
    @Query("UPDATE OtpVerification o SET o.used = true WHERE o.mobile = :mobile AND o.otpType = :otpType AND o.used = false")
    void markAllUsedByMobileAndType(String mobile, OtpType otpType);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :before")
    void deleteExpiredOtps(LocalDateTime before);
}
