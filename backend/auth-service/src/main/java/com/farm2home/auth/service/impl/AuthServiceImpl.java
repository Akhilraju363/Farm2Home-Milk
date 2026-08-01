package com.farm2home.auth.service.impl;

import com.farm2home.auth.domain.entity.RefreshToken;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.enums.RoleType;
import com.farm2home.auth.domain.repository.RefreshTokenRepository;
import com.farm2home.auth.domain.repository.RoleRepository;
import com.farm2home.auth.domain.repository.UserRepository;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.exception.ResourceNotFoundException;
import com.farm2home.auth.kafka.CustomerEventProducer;
import com.farm2home.auth.mapper.UserMapper;
import com.farm2home.auth.service.AuthService;
import com.farm2home.auth.service.JwtService;
import com.farm2home.auth.service.OtpService;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
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
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final CustomerEventProducer customerEventProducer;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByMobileAndDeletedFalse(request.getMobile())) {
            throw new AuthException("Mobile number is already registered.");
        }
        if (request.getEmail() != null && userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new AuthException("Email address is already registered.");
        }

        var customerRole = roleRepository.findByNameAndDeletedFalse(RoleType.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role CUSTOMER not found. Run database seed."));

        User user = userMapper.toEntity(request);
        user.setUsername(request.getFirstName().toLowerCase() + "." + request.getLastName().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setVerified(false);
        user.setRoles(Set.of(customerRole));

        user = userRepository.save(user);

        customerEventProducer.publishCustomerCreated(user,
                request.getFirstName() + " " + request.getLastName());

        // Send OTP for mobile verification
        otpService.generateAndSend(request.getMobile(), OtpType.REGISTRATION);

        log.info("User registered: {} — OTP sent for verification", request.getMobile());
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.CREATE)
                .entityType("User")
                .entityId(user.getId().toString())
                .username(user.getMobile())
                .details("User registered: " + user.getMobile())
                .build());

        // Return tokens but user.isEnabled() is false until verified
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getMobile(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new AuthException("Invalid mobile number or password.");
        } catch (AuthenticationException e) {
            throw new AuthException("Authentication failed: " + e.getMessage());
        }

        User user = userRepository.findByMobileAndDeletedFalse(request.getMobile())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        // Revoke previous refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        log.info("User logged in: {}", request.getMobile());
        auditLogService.record(AuditAction.LOGIN, "User", user.getId().toString(), user.getMobile(),
                "User logged in");
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void sendOtp(OtpRequest request) {
        otpService.generateAndSend(request.getMobile(), request.getOtpType());
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        otpService.verify(request.getMobile(), request.getOtp(), request.getOtpType());

        if (request.getOtpType() == OtpType.REGISTRATION) {
            User user = userRepository.findByMobileAndDeletedFalse(request.getMobile())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            user.setVerified(true);
            userRepository.save(user);
            log.info("User verified via OTP: {}", request.getMobile());
        }
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
