package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.RefreshToken;
import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.RoleType;
import com.farm2home.auth.domain.repository.RefreshTokenRepository;
import com.farm2home.auth.domain.repository.RoleRepository;
import com.farm2home.auth.domain.repository.UserIdentityRepository;
import com.farm2home.auth.domain.repository.UserRepository;
import com.farm2home.auth.dto.request.LoginRequest;
import com.farm2home.auth.dto.request.RefreshTokenRequest;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.kafka.CustomerEventProducer;
import com.farm2home.auth.mapper.UserMapper;
import com.farm2home.auth.service.impl.AuthServiceImpl;
import com.farm2home.common.core.audit.AuditLogService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer regression coverage for the refresh-token collision fix, exercising
 * {@link AuthServiceImpl} wired with a REAL {@link JwtService} (unlike {@code AuthServiceImplTest},
 * which mocks JwtService entirely and therefore cannot reproduce or guard against this bug - a
 * mock stubbed to return a fixed literal string is unique by construction, regardless of what the
 * real token generator does).
 *
 * <p>The repository layer is simulated with a small mutable in-memory stand-in (not a real
 * database) that mirrors exactly what {@code auth.refresh_tokens} does: rows are looked up by
 * {@code (tokenHash, revoked=false)}, and revoking a row is visible to the next lookup - this is
 * enough to faithfully exercise AuthServiceImpl's real revoke-then-reissue logic without
 * Testcontainers (flaky in this environment - see AuthServiceApplicationTests) or schema changes.
 * True concurrent-request racing against a real transactional database was verified separately,
 * live, against a real Postgres instance (10 simultaneous HTTP calls presenting the identical
 * refresh token: exactly 1 succeeded, 9 correctly rejected, exactly one new row minted) - see
 * docs/PRODUCTION_READINESS.md. Mockito mocks have no real row-locking/transaction-isolation
 * semantics, so a thread-based "concurrency" test against them here would not be meaningful.
 */
class RefreshTokenServiceLayerRegressionTest {

    private static final String SECRET =
            "ZmFybTJob21lLXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private UserIdentityRepository userIdentityRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private OtpService otpService;
    private GoogleTokenValidator googleTokenValidator;
    private UserMapper userMapper;
    private AuditLogService auditLogService;
    private CustomerEventProducer customerEventProducer;

    private AuthServiceImpl service;
    private User user;

    /** Mirrors AuthServiceImpl.hashToken() exactly (SHA-256, lower-hex). */
    private static String sha256Hex(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userIdentityRepository = mock(UserIdentityRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        otpService = mock(OtpService.class);
        googleTokenValidator = mock(GoogleTokenValidator.class);
        userMapper = mock(UserMapper.class);
        auditLogService = mock(AuditLogService.class);
        customerEventProducer = mock(CustomerEventProducer.class);

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604_800_000L);

        service = new AuthServiceImpl(userRepository, roleRepository, refreshTokenRepository,
                userIdentityRepository, passwordEncoder, authenticationManager, jwtService,
                otpService, googleTokenValidator, userMapper, auditLogService, customerEventProducer);
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 604_800_000L);

        Role role = Role.builder().id(UUID.randomUUID()).name(RoleType.CUSTOMER).build();
        user = User.builder()
                .id(UUID.randomUUID())
                .mobile("9876543210")
                .username("john.doe")
                .passwordHash("hashed")
                .active(true)
                .verified(true)
                .roles(Set.of(role))
                .build();

        when(userRepository.findByIdentifierAndDeletedFalse(user.getMobile())).thenReturn(Optional.of(user));
    }

    private LoginRequest loginRequest() {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(user.getMobile());
        req.setPassword("Str0ng!Pass");
        return req;
    }

    @Nested
    @DisplayName("REGRESSION: two token issuances for the same user are independently tracked")
    class IndependentlyTracked {

        @Test
        @DisplayName("two back-to-back login() calls for the same user persist two RefreshToken rows with different hashes and different jti")
        void twoIssuancesPersistTwoDistinctRows() {
            AuthResponse first = service.login(loginRequest());
            AuthResponse second = service.login(loginRequest());

            assertThat(first.getRefreshToken()).isNotEqualTo(second.getRefreshToken());

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            List<RefreshToken> saved = captor.getAllValues();

            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).getTokenHash()).isNotEqualTo(saved.get(1).getTokenHash());
            // The stored hash is exactly sha256(the token string returned to the caller) -
            // proves the two "records" correspond to the two actually-issued tokens, not an
            // artifact of the test double.
            assertThat(saved.get(0).getTokenHash()).isEqualTo(sha256Hex(first.getRefreshToken()));
            assertThat(saved.get(1).getTokenHash()).isEqualTo(sha256Hex(second.getRefreshToken()));

            String jtiFirst = jwtService.extractClaim(first.getRefreshToken(), Claims::getId);
            String jtiSecond = jwtService.extractClaim(second.getRefreshToken(), Claims::getId);
            assertThat(jtiFirst).isNotEqualTo(jtiSecond);
        }
    }

    @Nested
    @DisplayName("REGRESSION: refresh token rotation (A -> revoked, C issued -> valid)")
    class Rotation {

        @Test
        @DisplayName("refresh token A rotates to access token B + refresh token C; A is then rejected; C is accepted")
        void fullRotationCycle() {
            // Issue the original pair via login() - this is refresh token "A".
            AuthResponse loginResponse = service.login(loginRequest());
            String tokenA = loginResponse.getRefreshToken();
            String hashA = sha256Hex(tokenA);

            RefreshToken storedA = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(user).tokenHash(hashA)
                    .expiresAt(LocalDateTime.now().plusDays(7)).revoked(false).build();

            // Answers dynamically from storedA's live 'revoked' flag - exactly what the real
            // findByTokenHashAndRevokedFalse(hash) + is_revoked column does in Postgres, without
            // needing a real database.
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(hashA))
                    .thenAnswer(inv -> storedA.isRevoked() ? Optional.empty() : Optional.of(storedA));

            // --- refresh token A ---
            AuthResponse rotated = service.refreshToken(refreshRequest(tokenA));
            String accessTokenB = rotated.getAccessToken();
            String tokenC = rotated.getRefreshToken();

            assertThat(accessTokenB).isNotBlank();
            assertThat(tokenC).isNotEqualTo(tokenA);
            assertThat(storedA.isRevoked()).as("A must be revoked immediately after successful rotation").isTrue();

            // --- A, presented again: must be rejected (single-use) ---
            assertThatThrownBy(() -> service.refreshToken(refreshRequest(tokenA)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or revoked");

            // --- C: must be independently valid ---
            String hashC = sha256Hex(tokenC);
            RefreshToken storedC = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(user).tokenHash(hashC)
                    .expiresAt(LocalDateTime.now().plusDays(7)).revoked(false).build();
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(hashC))
                    .thenAnswer(inv -> storedC.isRevoked() ? Optional.empty() : Optional.of(storedC));

            AuthResponse secondRotation = service.refreshToken(refreshRequest(tokenC));
            assertThat(secondRotation.getAccessToken()).isNotBlank();
            assertThat(storedC.isRevoked()).isTrue();
        }
    }

    @Nested
    @DisplayName("Step 7: refresh endpoint's acceptance/rejection matrix")
    class RefreshEndpointAcceptance {

        @Test
        @DisplayName("an access token presented to /refresh-token is rejected - it was never persisted to auth.refresh_tokens in the first place")
        void accessTokenRejectedByRefreshEndpoint() {
            String accessToken = jwtService.generateAccessToken(user);
            // Never stubbed to resolve to anything -> Optional.empty(), exactly what a real
            // lookup would return for a hash that was never inserted (access tokens are only
            // ever generated, never persisted/hashed for the refresh_tokens table).
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refreshToken(refreshRequest(accessToken)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or revoked");
        }

        @Test
        @DisplayName("a malformed string presented to /refresh-token is rejected - it cannot match any stored hash")
        void malformedTokenRejected() {
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refreshToken(refreshRequest("not-a-jwt-at-all")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or revoked");
        }

        @Test
        @DisplayName("a genuine refresh token with a tampered signature is rejected - the tampered string hashes differently from the original stored hash")
        void wrongSignatureTokenRejected() {
            String real = jwtService.generateRefreshToken(user);
            String tampered = real.substring(0, real.length() - 4) + "Xaaa"; // corrupt the signature only

            RefreshToken storedForReal = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(user).tokenHash(sha256Hex(real))
                    .expiresAt(LocalDateTime.now().plusDays(7)).revoked(false).build();
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(sha256Hex(real)))
                    .thenReturn(Optional.of(storedForReal));
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(sha256Hex(tampered)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refreshToken(refreshRequest(tampered)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid or revoked");
        }

        @Test
        @DisplayName("a genuine, unexpired, unrevoked refresh token is accepted")
        void validTokenAccepted() {
            String real = jwtService.generateRefreshToken(user);
            RefreshToken stored = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(user).tokenHash(sha256Hex(real))
                    .expiresAt(LocalDateTime.now().plusDays(7)).revoked(false).build();
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(sha256Hex(real)))
                    .thenReturn(Optional.of(stored));

            AuthResponse response = service.refreshToken(refreshRequest(real));

            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("an expired (but otherwise valid and unrevoked) stored refresh token is rejected and revoked")
        void expiredTokenRejectedAndRevoked() {
            String real = jwtService.generateRefreshToken(user);
            RefreshToken stored = RefreshToken.builder()
                    .id(UUID.randomUUID()).user(user).tokenHash(sha256Hex(real))
                    .expiresAt(LocalDateTime.now().minusSeconds(1)).revoked(false).build();
            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(sha256Hex(real)))
                    .thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> service.refreshToken(refreshRequest(real)))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("expired");
            assertThat(stored.isRevoked()).isTrue();
        }
    }

    private RefreshTokenRequest refreshRequest(String token) {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(token);
        return req;
    }
}
