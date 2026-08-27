package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.RefreshToken;
import com.farm2home.auth.domain.entity.Role;
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
import com.farm2home.auth.service.impl.AuthServiceImpl;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private UserIdentityRepository userIdentityRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private OtpService otpService;
    @Mock private GoogleTokenValidator googleTokenValidator;
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

        @Test
        @DisplayName("continuing a Google Sign-In (googleCredential present, matching email) → creates account and links the identity")
        void withGoogleCredential_matchingEmail_linksIdentity() {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("John");
            req.setLastName("Doe");
            req.setMobile(mobile);
            req.setEmail("john@example.com");
            req.setPassword("Passw0rd!");
            req.setGoogleCredential("valid-credential");

            GooglePayload payload = new GooglePayload("google-sub-9", "john@example.com", true, "John", "Doe");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-9"))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByMobileAndDeletedFalse(mobile)).thenReturn(false);
            when(userRepository.existsByEmailAndDeletedFalse("john@example.com")).thenReturn(false);
            when(roleRepository.findByNameAndDeletedFalse(RoleType.CUSTOMER)).thenReturn(Optional.of(customerRole()));
            when(userMapper.toEntity(req)).thenReturn(new User());
            when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed-pw");
            User saved = buildUser(false);
            when(userRepository.save(any(User.class))).thenReturn(saved);
            when(userIdentityRepository.existsByUserIdAndProvider(userId, IdentityProvider.GOOGLE)).thenReturn(false);
            stubJwtAndMapperForBuildAuthResponse(saved);

            service.register(req);

            ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
            verify(userIdentityRepository).save(captor.capture());
            assertThat(captor.getValue().getProviderUserId()).isEqualTo("google-sub-9");
            verify(otpService).generateAndSend(mobile, OtpType.REGISTRATION); // still re-verifies the mobile
        }

        @Test
        @DisplayName("SECURITY: googleCredential present but its email doesn't match the request email → rejected, no account created")
        void withGoogleCredential_mismatchedEmail_throws() {
            RegisterRequest req = new RegisterRequest();
            req.setMobile(mobile);
            req.setEmail("john@example.com");
            req.setGoogleCredential("valid-credential");

            GooglePayload payload = new GooglePayload("google-sub-10", "someone-else@example.com", true, "X", "Y");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("does not match");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("googleCredential's identity is already linked to a different account → rejected, no duplicate link")
        void withGoogleCredential_alreadyLinked_throws() {
            RegisterRequest req = new RegisterRequest();
            req.setMobile(mobile);
            req.setEmail("john@example.com");
            req.setGoogleCredential("valid-credential");

            GooglePayload payload = new GooglePayload("google-sub-11", "john@example.com", true, "John", "Doe");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-11"))
                    .thenReturn(Optional.of(UserIdentity.builder().build()));

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(userRepository, never()).save(any());
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
        @DisplayName("verifyOtp with REGISTRATION type → marks user verified, returns no tokens")
        void verifyOtp_registration_marksVerified() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile(mobile);
            req.setOtp("123456");
            req.setOtpType(OtpType.REGISTRATION);

            User user = buildUser(false);
            when(userRepository.findByMobileAndDeletedFalse(mobile)).thenReturn(Optional.of(user));

            OtpVerifyResponse response = service.verifyOtp(req);

            verify(otpService).verify(mobile, "123456", OtpType.REGISTRATION);
            assertThat(user.isVerified()).isTrue();
            verify(userRepository).save(user);
            assertThat(response.isRegistrationRequired()).isFalse();
            assertThat(response.getAuth()).isNull();
        }

        @Test
        @DisplayName("verifyOtp with FORGOT_PASSWORD type → does not touch verified flag, returns no tokens")
        void verifyOtp_forgotPassword_returnsNoTokens() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile(mobile);
            req.setOtp("123456");
            req.setOtpType(OtpType.FORGOT_PASSWORD);

            OtpVerifyResponse response = service.verifyOtp(req);

            verify(otpService).verify(mobile, "123456", OtpType.FORGOT_PASSWORD);
            verify(userRepository, never()).save(any());
            assertThat(response.isRegistrationRequired()).isFalse();
            assertThat(response.getAuth()).isNull();
        }

        @Test
        @DisplayName("verifyOtp with LOGIN type, existing account → signs in, returns tokens")
        void verifyOtp_login_existingAccount_signsIn() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile(mobile);
            req.setOtp("123456");
            req.setOtpType(OtpType.LOGIN);

            User user = buildUser(true);
            when(userRepository.findByMobileAndDeletedFalse(mobile)).thenReturn(Optional.of(user));
            stubJwtAndMapperForBuildAuthResponse(user);

            OtpVerifyResponse response = service.verifyOtp(req);

            verify(otpService).verify(mobile, "123456", OtpType.LOGIN);
            verify(refreshTokenRepository).revokeAllByUser(user);
            assertThat(response.isRegistrationRequired()).isFalse();
            assertThat(response.getAuth().getAccessToken()).isEqualTo("access-token");
            verify(auditLogService).record(eq(AuditAction.LOGIN), eq("User"), eq(userId.toString()), eq(mobile), any());
        }

        @Test
        @DisplayName("verifyOtp with LOGIN type, no account for this mobile → registrationRequired, no tokens, no account created")
        void verifyOtp_login_noAccount_registrationRequired() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile(mobile);
            req.setOtp("123456");
            req.setOtpType(OtpType.LOGIN);

            when(userRepository.findByMobileAndDeletedFalse(mobile)).thenReturn(Optional.empty());

            OtpVerifyResponse response = service.verifyOtp(req);

            verify(otpService).verify(mobile, "123456", OtpType.LOGIN);
            assertThat(response.isRegistrationRequired()).isTrue();
            assertThat(response.getAuth()).isNull();
            verify(userRepository, never()).save(any());
        }
    }

    // ── googleAuth() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("googleAuth()")
    class GoogleAuth {

        private GoogleAuthRequest req() {
            GoogleAuthRequest r = new GoogleAuthRequest();
            r.setCredential("valid-credential");
            return r;
        }

        @Test
        @DisplayName("returning Google user (already linked) → signs in by (provider, subject), never by email")
        void returningGoogleUser_signsIn() {
            GooglePayload payload = new GooglePayload("google-sub-1", "john@example.com", true, "John", "Doe");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            User user = buildUser(true);
            UserIdentity identity = UserIdentity.builder().user(user).provider(IdentityProvider.GOOGLE)
                    .providerUserId("google-sub-1").build();
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-1"))
                    .thenReturn(Optional.of(identity));
            stubJwtAndMapperForBuildAuthResponse(user);

            GoogleAuthResponse response = service.googleAuth(req());

            assertThat(response.isRegistrationRequired()).isFalse();
            assertThat(response.getAuth().getAccessToken()).isEqualTo("access-token");
            verify(userRepository, never()).findByEmailAndDeletedFalse(any());
            verify(userIdentityRepository, never()).save(any());
        }

        @Test
        @DisplayName("first Google sign-in, verified email matches an existing account → auto-links and signs in")
        void firstSignIn_verifiedEmailMatch_autoLinksAndSignsIn() {
            GooglePayload payload = new GooglePayload("google-sub-2", "john@example.com", true, "John", "Doe");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-2"))
                    .thenReturn(Optional.empty());
            User user = buildUser(true);
            when(userRepository.findByEmailAndDeletedFalse("john@example.com")).thenReturn(Optional.of(user));
            when(userIdentityRepository.existsByUserIdAndProvider(userId, IdentityProvider.GOOGLE)).thenReturn(false);
            stubJwtAndMapperForBuildAuthResponse(user);

            GoogleAuthResponse response = service.googleAuth(req());

            assertThat(response.isRegistrationRequired()).isFalse();
            assertThat(response.getAuth().getAccessToken()).isEqualTo("access-token");
            ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
            verify(userIdentityRepository).save(captor.capture());
            assertThat(captor.getValue().getProviderUserId()).isEqualTo("google-sub-2");
            assertThat(captor.getValue().getUser()).isEqualTo(user);
        }

        @Test
        @DisplayName("SECURITY: first Google sign-in with an UNVERIFIED email matching an existing account → never auto-links, never signs in")
        void firstSignIn_unverifiedEmailMatch_neverLinksOrSignsIn() {
            GooglePayload payload = new GooglePayload("google-sub-3", "john@example.com", false, "John", "Doe");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-3"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailAndDeletedFalse("john@example.com")).thenReturn(Optional.of(buildUser(true)));

            assertThatThrownBy(() -> service.googleAuth(req()))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("already exists");

            verify(userIdentityRepository, never()).save(any());
            verify(jwtService, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("no existing identity and no matching email → registrationRequired, pre-filled from Google, no account created")
        void noMatch_registrationRequired() {
            GooglePayload payload = new GooglePayload("google-sub-4", "new@example.com", true, "Jane", "Smith");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-4"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailAndDeletedFalse("new@example.com")).thenReturn(Optional.empty());

            GoogleAuthResponse response = service.googleAuth(req());

            assertThat(response.isRegistrationRequired()).isTrue();
            assertThat(response.getAuth()).isNull();
            assertThat(response.getFirstName()).isEqualTo("Jane");
            assertThat(response.getLastName()).isEqualTo("Smith");
            assertThat(response.getEmail()).isEqualTo("new@example.com");
            verify(userRepository, never()).save(any());
            verify(userIdentityRepository, never()).save(any());
        }

        @Test
        @DisplayName("SECURITY: role is never accepted from the Google credential - existing user keeps their own role")
        void existingUserRole_neverOverriddenByGoogle() {
            GooglePayload payload = new GooglePayload("google-sub-5", "john@example.com", true, "John", "Doe");
            when(googleTokenValidator.validate("valid-credential")).thenReturn(payload);
            User user = buildUser(true); // CUSTOMER role, from buildUser()
            UserIdentity identity = UserIdentity.builder().user(user).provider(IdentityProvider.GOOGLE)
                    .providerUserId("google-sub-5").build();
            when(userIdentityRepository.findByProviderAndProviderUserId(IdentityProvider.GOOGLE, "google-sub-5"))
                    .thenReturn(Optional.of(identity));
            stubJwtAndMapperForBuildAuthResponse(user);

            service.googleAuth(req());

            // The only role-bearing object anywhere in this flow is the pre-existing User itself -
            // never reassigned, never read from GooglePayload (which has no role field at all).
            assertThat(user.getRoles()).extracting(r -> r.getName()).containsExactly(RoleType.CUSTOMER);
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
