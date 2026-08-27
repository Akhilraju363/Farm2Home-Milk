package com.farm2home.auth.service.impl;

import com.farm2home.auth.domain.entity.RefreshToken;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.entity.UserIdentity;
import com.farm2home.auth.domain.enums.IdentityProvider;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.enums.RoleType;
import com.farm2home.auth.domain.repository.RefreshTokenRepository;
import com.farm2home.auth.domain.repository.RoleRepository;
import com.farm2home.auth.domain.repository.UserIdentityRepository;
import com.farm2home.auth.domain.repository.UserRepository;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.dto.response.GoogleAuthResponse;
import com.farm2home.auth.dto.response.OtpVerifyResponse;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.exception.DuplicateResourceException;
import com.farm2home.auth.exception.ResourceNotFoundException;
import com.farm2home.auth.kafka.CustomerEventProducer;
import com.farm2home.auth.mapper.UserMapper;
import com.farm2home.auth.service.AuthService;
import com.farm2home.auth.service.GooglePayload;
import com.farm2home.auth.service.GoogleTokenValidator;
import com.farm2home.auth.service.JwtService;
import com.farm2home.auth.service.OtpService;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.RegexConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final GoogleTokenValidator googleTokenValidator;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final CustomerEventProducer customerEventProducer;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByMobileAndDeletedFalse(request.getMobile())) {
            throw new DuplicateResourceException("Mobile number is already registered.");
        }
        if (request.getEmail() != null && userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new DuplicateResourceException("Email address is already registered.");
        }

        // Continuing a Google Sign-In that found no existing account (see googleAuth()'s
        // registrationRequired=true response) - re-validate the same credential rather than
        // trusting that a prior /auth/google call actually happened, and require its email to
        // match exactly so a caller can't attach an unrelated Google identity to a different
        // email's registration. Never role-related: this only ever links an identity, the role
        // assignment below is completely unaffected either way.
        GooglePayload googlePayload = null;
        if (request.getGoogleCredential() != null && !request.getGoogleCredential().isBlank()) {
            googlePayload = googleTokenValidator.validate(request.getGoogleCredential());
            if (request.getEmail() != null && googlePayload.email() != null
                    && !request.getEmail().equalsIgnoreCase(googlePayload.email())) {
                throw new AuthException("Google account email does not match the email provided.");
            }
            if (userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, googlePayload.subject()).isPresent()) {
                throw new DuplicateResourceException("This Google account is already linked to a Farm2Home account.");
            }
        }

        var customerRole = roleRepository.findByNameAndDeletedFalse(RoleType.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role CUSTOMER not found. Run database seed."));

        User user = userMapper.toEntity(request);
        user.setUsername(request.getFirstName().toLowerCase() + "." + request.getLastName().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        // TODO: set back to false once real-time mobile OTP delivery is integrated (SmsService
        // is currently a stub), and require verifyOtp() before isEnabled() allows login.
        user.setVerified(true);
        user.setRoles(Set.of(customerRole));

        user = userRepository.save(user);

        if (googlePayload != null) {
            linkGoogleIdentity(user, googlePayload);
        }

        customerEventProducer.publishCustomerCreated(user, request.getFirstName(), request.getLastName());

        // Send OTP for mobile verification
        otpService.generateAndSend(request.getMobile(), OtpType.REGISTRATION);

        log.info("User registered: {} — OTP sent for verification", request.getMobile());
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.CREATE)
                .entityType("User")
                .entityId(user.getId().toString())
                .username(user.getMobile())
                .details("User registered: " + user.getMobile()
                        + (googlePayload != null ? " (Google-linked)" : ""))
                .build());

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new AuthException("Invalid mobile number/email/username or password.");
        } catch (AuthenticationException e) {
            throw new AuthException("Authentication failed: " + e.getMessage());
        }

        User user = userRepository.findByIdentifierAndDeletedFalse(request.getIdentifier())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        // Revoke previous refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        log.info("User logged in: {}", user.getMobile());
        auditLogService.record(AuditAction.LOGIN, "User", user.getId().toString(), user.getMobile(),
                "User logged in");
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void sendOtp(OtpRequest request) {
        String mobile = resolveMobileForOtp(request.getIdentifier());
        if (mobile == null) {
            // Email-shaped identifier that matches no account - stay silent rather than ever
            // confirming/denying whether it's registered (classic forgot-password enumeration guard).
            return;
        }
        otpService.generateAndSend(mobile, request.getOtpType());
    }

    /** A mobile-shaped identifier is used as-is - matches the existing REGISTRATION-resend
     *  behavior, which has never required the mobile to belong to an existing user. An
     *  email-shaped identifier must resolve to a real account's mobile number (OTPs are always
     *  SMS-delivered); see the null-handling in sendOtp() above for why an unresolvable email
     *  isn't treated as an error. */
    private String resolveMobileForOtp(String identifier) {
        if (identifier != null && identifier.matches(RegexConstants.MOBILE_PATTERN)) {
            return identifier;
        }
        return userRepository.findByEmailAndDeletedFalse(identifier).map(User::getMobile).orElse(null);
    }

    // noRollbackFor mirrors OtpService.verify()'s own annotation and is required for the same
    // reason: otpService.verify() joins THIS method's transaction (default REQUIRED propagation),
    // so its own noRollbackFor is only actually honored if this outer boundary agrees - Spring
    // marks a shared physical transaction rollback-only if ANY participating advice sees a
    // RuntimeException it doesn't explicitly exempt, regardless of what other participants say.
    // Without this, the OtpVerification.attempts increment inside verify()'s wrong-guess path is
    // silently discarded on every call, defeating the attempt limit entirely (live-verified to
    // actually happen - see AUTH_SOCIAL_OTP_PROGRESS.md). Safe here specifically because
    // otpService.verify() is the very first statement in this method - nothing before it could
    // need rolling back, and nothing after it runs at all once it throws.
    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public OtpVerifyResponse verifyOtp(VerifyOtpRequest request) {
        otpService.verify(request.getMobile(), request.getOtp(), request.getOtpType());

        if (request.getOtpType() == OtpType.REGISTRATION) {
            User user = userRepository.findByMobileAndDeletedFalse(request.getMobile())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            user.setVerified(true);
            userRepository.save(user);
            log.info("User verified via OTP: {}", request.getMobile());
            return OtpVerifyResponse.builder().registrationRequired(false).auth(null).build();
        }

        if (request.getOtpType() == OtpType.LOGIN) {
            return userRepository.findByMobileAndDeletedFalse(request.getMobile())
                    .map(user -> {
                        refreshTokenRepository.revokeAllByUser(user);
                        log.info("User logged in via Mobile OTP: {}", user.getMobile());
                        auditLogService.record(AuditAction.LOGIN, "User", user.getId().toString(), user.getMobile(),
                                "User logged in via Mobile OTP");
                        return OtpVerifyResponse.builder().registrationRequired(false).auth(buildAuthResponse(user)).build();
                    })
                    // Verified ownership of this mobile number, but no account exists yet - the
                    // frontend continues into the existing registration wizard (mobile pre-filled,
                    // still re-verified there via its own REGISTRATION OTP - see
                    // AUTH_SOCIAL_OTP_PROGRESS.md for why that's an accepted, deliberate redundancy
                    // rather than a bug) instead of a silently-incomplete account being created here.
                    .orElseGet(() -> OtpVerifyResponse.builder().registrationRequired(true).auth(null).build());
        }

        // FORGOT_PASSWORD - unchanged: verifying only proves mobile ownership, never signs in.
        return OtpVerifyResponse.builder().registrationRequired(false).auth(null).build();
    }

    @Override
    @Transactional
    public GoogleAuthResponse googleAuth(GoogleAuthRequest request) {
        GooglePayload payload = googleTokenValidator.validate(request.getCredential());

        // 1) Returning Google user - stable lookup by (provider, subject), never by email.
        var existingIdentity = userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, payload.subject());
        if (existingIdentity.isPresent()) {
            User user = existingIdentity.get().getUser();
            return signInExistingUser(user, "Google");
        }

        // 2) First time this Google subject has signed in - see if it belongs to an existing
        // password-based account by email. Only safe to auto-link when Google itself vouches for
        // the email (emailVerified=true) - an unverified email match must never grant access to
        // someone else's account (see AUTH_SOCIAL_OTP_PROGRESS.md Scenario B).
        if (payload.email() != null) {
            var existingUser = userRepository.findByEmailAndDeletedFalse(payload.email());
            if (existingUser.isPresent()) {
                if (!payload.emailVerified()) {
                    throw new AuthException(
                            "An account with this email already exists. Please log in with your password.");
                }
                User user = existingUser.get();
                linkGoogleIdentity(user, payload);
                return signInExistingUser(user, "Google (linked)");
            }
        }

        // 3) Genuinely new customer - mobile is mandatory on every Farm2Home account (SMS OTP,
        // delivery notifications) and Google never supplies one, so no account is created here.
        // The frontend continues into the existing registration wizard, pre-filled with the
        // validated name/email, which will call POST /auth/register with the same credential
        // attached (see register()'s googleCredential handling) once the customer supplies a
        // mobile number, password, address, and consent - the exact same path any other new
        // customer goes through, never a parallel/duplicated registration flow.
        return GoogleAuthResponse.builder()
                .registrationRequired(true)
                .firstName(payload.firstName())
                .lastName(payload.lastName())
                .email(payload.email())
                .build();
    }

    private GoogleAuthResponse signInExistingUser(User user, String via) {
        refreshTokenRepository.revokeAllByUser(user);
        log.info("User logged in via {}: {}", via, user.getMobile());
        auditLogService.record(AuditAction.LOGIN, "User", user.getId().toString(), user.getMobile(),
                "User logged in via " + via);
        return GoogleAuthResponse.builder().registrationRequired(false).auth(buildAuthResponse(user)).build();
    }

    /** Never lets one user link the same provider twice (existsByUserIdAndProvider) - not
     *  because a second Google account for the same user is dangerous, just because there's no
     *  product concept of "which one" a future lookup by (provider, subject) should have
     *  preferred, and it isn't needed for anything this task asked for. */
    private void linkGoogleIdentity(User user, GooglePayload payload) {
        if (userIdentityRepository.existsByUserIdAndProvider(user.getId(), IdentityProvider.GOOGLE)) {
            return;
        }
        String displayName = ((payload.firstName() != null ? payload.firstName() : "")
                + " " + (payload.lastName() != null ? payload.lastName() : "")).trim();
        userIdentityRepository.save(UserIdentity.builder()
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .providerUserId(payload.subject())
                .email(payload.email())
                .emailVerified(payload.emailVerified())
                .displayName(displayName.isEmpty() ? null : displayName)
                .build());
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new AuthException("Invalid or revoked refresh token."));

        if (storedToken.isExpired()) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new AuthException("Refresh token has expired. Please log in again.");
        }

        User user = storedToken.getUser();
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        log.info("Token refreshed for user: {}", user.getMobile());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        String mobile = jwtService.extractMobile(accessToken.replace(SecurityConstants.BEARER_PREFIX, ""));
        User user = userRepository.findByMobileAndDeletedFalse(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        refreshTokenRepository.revokeAllByUser(user);
        log.info("User logged out: {}", mobile);
        auditLogService.record(AuditAction.LOGOUT, "User", user.getId().toString(), user.getMobile(),
                "User logged out");
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUserInfo(User user) {
        return userMapper.toUserInfo(user);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshToken(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(refreshTokenExpiration / 1000)
                .user(userMapper.toUserInfo(user))
                .build();
    }

    private String hashToken(String token) {
        // Simple SHA-256 hash for storing refresh tokens
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
