package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.RefreshToken;
import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.enums.RoleType;
import com.farm2home.auth.domain.repository.RefreshTokenRepository;
import com.farm2home.auth.domain.repository.RoleRepository;
import com.farm2home.auth.domain.repository.UserRepository;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.exception.DuplicateResourceException;
import com.farm2home.auth.exception.ResourceNotFoundException;
import com.farm2home.auth.kafka.CustomerEventProducer;
import com.farm2home.auth.mapper.UserMapper;
import com.farm2home.auth.service.impl.AuthServiceImpl;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private OtpService otpService;
    @Mock private UserMapper userMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private CustomerEventProducer customerEventProducer;

    @InjectMocks private AuthServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final String mobile = "9876543210";

    @BeforeEach
    void setRefreshExpiration() {
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 604_800_000L); // 7 days ms
    }

    private Role customerRole() {
        return Role.builder().id(UUID.randomUUID()).name(RoleType.CUSTOMER).build();
    }

    private User buildUser(boolean verified) {
        return User.builder()
                .id(userId)
                .username("john.doe")
                .mobile(mobile)
                .email("john@example.com")
                .passwordHash("hashed")
                .active(true)
                .verified(verified)
                .roles(Set.of(customerRole()))
                .build();
    }

    private void stubJwtAndMapperForBuildAuthResponse(User user) {
        lenient().when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        lenient().when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token-value");
        lenient().when(userMapper.toUserInfo(user)).thenReturn(
                AuthResponse.UserInfo.builder().id(userId).mobile(mobile).build());
    }

    // ── register() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("valid new user → creates account, sends OTP, returns tokens")
        void happyPath() {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("John");
            req.setLastName("Doe");
            req.setMobile(mobile);
            req.setEmail("john@example.com");
            req.setPassword("Passw0rd!");

            when(userRepository.existsByMobileAndDeletedFalse(mobile)).thenReturn(false);
            when(userRepository.existsByEmailAndDeletedFalse("john@example.com")).thenReturn(false);
            Role role = customerRole();
            when(roleRepository.findByNameAndDeletedFalse(RoleType.CUSTOMER)).thenReturn(Optional.of(role));
            when(userMapper.toEntity(req)).thenReturn(new User());
            when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed-pw");

            User saved = buildUser(false);
            when(userRepository.save(any(User.class))).thenReturn(saved);
            stubJwtAndMapperForBuildAuthResponse(saved);

            AuthResponse response = service.register(req);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            verify(otpService).generateAndSend(mobile, OtpType.REGISTRATION);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("mobile already registered → throws DuplicateResourceException (409, not 401 - this isn't an auth failure)")
        void duplicateMobile_throws() {
            RegisterRequest req = new RegisterRequest();
            req.setMobile(mobile);
            when(userRepository.existsByMobileAndDeletedFalse(mobile)).thenReturn(true);

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already registered");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("email already registered → throws DuplicateResourceException (409, not 401 - this isn't an auth failure)")
        void duplicateEmail_throws() {
            RegisterRequest req = new RegisterRequest();
            req.setMobile(mobile);
            req.setEmail("john@example.com");
            when(userRepository.existsByMobileAndDeletedFalse(mobile)).thenReturn(false);
            when(userRepository.existsByEmailAndDeletedFalse("john@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Email");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("CUSTOMER role missing from DB → throws ResourceNotFoundException")
        void roleNotSeeded_throws() {
            RegisterRequest req = new RegisterRequest();
            req.setMobile(mobile);
            when(userRepository.existsByMobileAndDeletedFalse(mobile)).thenReturn(false);
            when(roleRepository.findByNameAndDeletedFalse(RoleType.CUSTOMER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── login() ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("valid credentials → authenticates, revokes old tokens, returns new tokens")
        void happyPath() {
            LoginRequest req = new LoginRequest();
            req.setIdentifier(mobile);
            req.setPassword("Passw0rd!");

            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken(mobile, "Passw0rd!"));
            User user = buildUser(true);
            when(userRepository.findByIdentifierAndDeletedFalse(mobile)).thenReturn(Optional.of(user));
            stubJwtAndMapperForBuildAuthResponse(user);

            AuthResponse response = service.login(req);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            verify(refreshTokenRepository).revokeAllByUser(user);
            verify(auditLogService).record(eq(AuditAction.LOGIN), eq("User"), eq(userId.toString()), eq(mobile), any());
        }

        @Test
        @DisplayName("bad credentials → throws AuthException")
        void badCredentials_throws() {
            LoginRequest req = new LoginRequest();
            req.setIdentifier(mobile);
            req.setPassword("wrong");
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid mobile number/email/username or password");
        }

        @Test
        @DisplayName("other authentication failure → throws AuthException")
        void otherAuthFailure_throws() {
            LoginRequest req = new LoginRequest();
            req.setIdentifier(mobile);
            req.setPassword("wrong");
            when(authenticationManager.authenticate(any())).thenThrow(
                    new AuthenticationException("locked") {});

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Authentication failed");
        }
    }

    // ── sendOtp() / verifyOtp() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("sendOtp() / verifyOtp()")
    class Otp {

        @Test
        @DisplayName("sendOtp with a mobile identifier → delegates to OtpService as-is")
        void sendOtp_mobileIdentifier_delegates() {
            OtpRequest req = new OtpRequest();
            req.setIdentifier(mobile);
            req.setOtpType(OtpType.LOGIN);

            service.sendOtp(req);

            verify(otpService).generateAndSend(mobile, OtpType.LOGIN);
        }

        @Test
        @DisplayName("sendOtp with a registered email identifier → resolves to the account's mobile")
        void sendOtp_registeredEmailIdentifier_resolvesToMobile() {
            String email = "asha.rao@example.com";
            OtpRequest req = new OtpRequest();
            req.setIdentifier(email);
            req.setOtpType(OtpType.FORGOT_PASSWORD);

            User user = buildUser(true);
            when(userRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(user));

            service.sendOtp(req);

            verify(otpService).generateAndSend(user.getMobile(), OtpType.FORGOT_PASSWORD);
        }

        @Test
        @DisplayName("sendOtp with an unregistered email identifier → silently does nothing")
        void sendOtp_unregisteredEmailIdentifier_noOp() {
            String email = "nobody@example.com";
            OtpRequest req = new OtpRequest();
            req.setIdentifier(email);
            req.setOtpType(OtpType.FORGOT_PASSWORD);

            when(userRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.empty());

            service.sendOtp(req);

            verifyNoInteractions(otpService);
        }

        @Test
        @DisplayName("verifyOtp with REGISTRATION type → marks user verified")
        void verifyOtp_registration_marksVerified() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile(mobile);
            req.setOtp("123456");
            req.setOtpType(OtpType.REGISTRATION);

            User user = buildUser(false);
            when(userRepository.findByMobileAndDeletedFalse(mobile)).thenReturn(Optional.of(user));

            service.verifyOtp(req);

            verify(otpService).verify(mobile, "123456", OtpType.REGISTRATION);
            assertThat(user.isVerified()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("verifyOtp with LOGIN type → does not touch verified flag")
        void verifyOtp_login_doesNotMarkVerified() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile(mobile);
            req.setOtp("123456");
            req.setOtpType(OtpType.LOGIN);

            service.verifyOtp(req);

            verify(otpService).verify(mobile, "123456", OtpType.LOGIN);
            verify(userRepository, never()).findByMobileAndDeletedFalse(any());
            verify(userRepository, never()).save(any());
        }
    }

    // ── refreshToken() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshToken()")
    class Refresh {

        @Test
        @DisplayName("valid, unexpired token → revokes it and issues new tokens")
        void happyPath() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("some-refresh-token");

            User user = buildUser(true);
            RefreshToken stored = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(user)
                    .expiresAt(LocalDateTime.now().plusDays(1)).revoked(false).build();

            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(any())).thenReturn(Optional.of(stored));
            stubJwtAndMapperForBuildAuthResponse(user);

            AuthResponse response = service.refreshToken(req);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(stored.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("unknown/revoked token → throws AuthException")
        void notFound_throws() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("bogus");
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refreshToken(req))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or revoked");
        }

        @Test
        @DisplayName("expired token → revokes it and throws AuthException")
        void expired_throws() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("expired-token");

            RefreshToken stored = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(buildUser(true))
                    .expiresAt(LocalDateTime.now().minusDays(1)).revoked(false).build();
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(any())).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> service.refreshToken(req))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("expired");
            assertThat(stored.isRevoked()).isTrue();
        }
    }

    // ── logout() ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("valid bearer token → extracts mobile, revokes all tokens, audits")
        void happyPath() {
            when(jwtService.extractMobile("valid-token")).thenReturn(mobile);
            User user = buildUser(true);
            when(userRepository.findByMobileAndDeletedFalse(mobile)).thenReturn(Optional.of(user));

            service.logout("Bearer valid-token");

            verify(refreshTokenRepository).revokeAllByUser(user);
            verify(auditLogService).record(eq(AuditAction.LOGOUT), eq("User"), eq(userId.toString()), eq(mobile), any());
        }

        @Test
        @DisplayName("user no longer exists → throws ResourceNotFoundException")
        void userMissing_throws() {
            when(jwtService.extractMobile("valid-token")).thenReturn(mobile);
            when(userRepository.findByMobileAndDeletedFalse(mobile)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.logout("Bearer valid-token"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
