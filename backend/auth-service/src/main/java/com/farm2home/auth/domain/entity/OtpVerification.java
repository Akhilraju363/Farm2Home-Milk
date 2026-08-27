package com.farm2home.auth.domain.entity;

import com.farm2home.auth.domain.enums.OtpType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_verifications", schema = "auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mobile", nullable = false)
    private String mobile;

    /** A BCrypt hash of the 6-digit OTP (same PasswordEncoder already used for user passwords -
     *  see OtpService), never the raw code. Column is sized for a hash (VARCHAR(255) as of
     *  V4__social_login_and_otp_hardening.sql), not the 6 raw digits. */
    @Column(name = "otp", nullable = false)
    private String otp;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type", nullable = false)
    private OtpType otpType;

    @Column(name = "is_used")
    @Builder.Default
    private boolean used = false;

    /** Incremented on every failed verify() attempt against this specific record - once it
     *  reaches OtpProperties.maxAttempts, the record is burned (marked used) even though it was
     *  never successfully verified, so a wrong-guess loop can't keep trying against one code
     *  indefinitely. Resets naturally on the next generateAndSend() call, which always creates a
     *  brand new record (see markAllUsedByMobileAndType). */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
