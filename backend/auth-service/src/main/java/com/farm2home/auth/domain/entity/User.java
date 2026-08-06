package com.farm2home.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "users", schema = "auth")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"passwordHash", "roles"})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "mobile", unique = true, nullable = false)
    private String mobile;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_verified")
    @Builder.Default
    private boolean verified = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        schema = "auth",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    @CreatedBy
    private String createdBy;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    @LastModifiedBy
    private String updatedBy;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean deleted = false;

    // ── UserDetails ────────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security uses mobile as the principal identifier. This shadows Lombok's own
     *  generated getter for the `username` field below - anything that needs the actual
     *  username column (e.g. UserMapper#toUserInfo) must go through getAccountUsername()
     *  instead of getUsername(). */
    @Override
    public String getUsername() {
        return mobile;
    }

    /** The account's actual username column, distinct from getUsername() above (which Spring
     *  Security's UserDetails contract repurposes to return the mobile number instead). */
    public String getAccountUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active && verified;
    }
}
