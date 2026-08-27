package com.farm2home.auth.domain.entity;

import com.farm2home.auth.domain.enums.IdentityProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Links an external identity provider (Google) to an existing {@link User} row - see
 *  AUTH_SOCIAL_OTP_PROGRESS.md's account-linking section. Deliberately a separate table rather
 *  than columns on User itself: a user may in principle link more than one provider over time,
 *  and User already carries the platform's own password-based identity - this mirrors the
 *  "User has password credentials + Google identity + mobile identity" model the task asked for
 *  without touching the existing users table at all. */
@Entity
@Table(name = "user_identities", schema = "auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private IdentityProvider provider;

    /** The provider's own stable subject id (Google's "sub" claim) - never the email, which can
     *  change on the provider's side and must never be used as a permanent identity key. */
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
