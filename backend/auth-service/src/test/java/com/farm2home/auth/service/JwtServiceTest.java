package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.RoleType;
import com.farm2home.common.core.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Same well-formed 256-bit+ base64 test secret used elsewhere in this repo's default config.
    private static final String SECRET =
            "ZmFybTJob21lLXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604_800_000L);

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
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("produces a token whose subject and claims round-trip correctly")
        void roundTrips() {
            String token = jwtService.generateAccessToken(user);

            assertThat(token).isNotBlank();
            assertThat(jwtService.extractMobile(token)).isEqualTo(user.getMobile());
            assertThat(jwtService.isTokenExpired(token)).isFalse();

            @SuppressWarnings("unchecked")
            List<String> roles = jwtService.extractClaim(token, c -> c.get(SecurityConstants.CLAIM_ROLES, List.class));
            assertThat(roles).containsExactly(SecurityConstants.ROLE_CUSTOMER);

            String userId = jwtService.extractClaim(token, c -> c.get(SecurityConstants.CLAIM_USER_ID, String.class));
            assertThat(userId).isEqualTo(user.getId().toString());

            String type = jwtService.extractClaim(token, c -> c.get(SecurityConstants.CLAIM_TYPE, String.class));
            assertThat(type).isEqualTo(SecurityConstants.TOKEN_TYPE_ACCESS);
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("produces a token with type REFRESH, no roles claim, and a unique jti")
        void hasRefreshType() {
            String token = jwtService.generateRefreshToken(user);

            assertThat(jwtService.extractMobile(token)).isEqualTo(user.getMobile());
            String type = jwtService.extractClaim(token, c -> c.get(SecurityConstants.CLAIM_TYPE, String.class));
            assertThat(type).isEqualTo(SecurityConstants.TOKEN_TYPE_REFRESH);

            String jti = jwtService.extractClaim(token, Claims::getId);
            assertThat(jti).isNotBlank();
            // Must be an opaque random identifier, not derived from anything sensitive/guessable
            // (Step 2: "do not expose unnecessary sensitive information in the jti").
            assertThat(jti).doesNotContain(user.getMobile()).doesNotContain(user.getId().toString());
            assertThatCode(() -> UUID.fromString(jti)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("access token carries no jti - uniqueness is a refresh-token-only concern (it is never persisted/hashed for lookup)")
        void accessTokenHasNoJti() {
            String token = jwtService.generateAccessToken(user);
            String jti = jwtService.extractClaim(token, Claims::getId);
            assertThat(jti).isNull();
        }
    }

    /**
     * REGRESSION for the refresh-token collision bug (see JwtService.generateRefreshToken's
     * javadoc). Reproduced live against a real Postgres instance before this fix: two refresh
     * tokens minted for the same user within the same wall-clock second were byte-identical
     * (jjwt encodes iat/exp as whole-second NumericDate claims; sub/userId/type were already
     * identical), so their SHA-256 hashes - what auth.refresh_tokens.token_hash stores - collided
     * too, and a lookup by (hash, revoked=false) could then match the wrong row, letting an
     * already-used refresh token be redeemed again. These tests exercise the REAL JwtService (not
     * mocked, unlike AuthServiceImplTest) since the bug lived entirely in token generation.
     */
    @Nested
    @DisplayName("REGRESSION: refresh-token collision (jti uniqueness)")
    class RefreshTokenCollisionRegression {

        /** Mirrors AuthServiceImpl.hashToken() exactly (SHA-256, lower-hex) - that private
         *  method is what auth.refresh_tokens.token_hash actually stores, so this is what proves
         *  "stored hashes are different" without needing a real repository/database. */
        private String sha256Hex(String token) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("two refresh tokens minted back-to-back for the same user (guaranteed same wall-clock second) are never identical, never share a jti, and never hash the same")
        void sequentialTokensNeverCollide() {
            String tokenA = jwtService.generateRefreshToken(user);
            String tokenB = jwtService.generateRefreshToken(user);

            assertThat(tokenA).isNotEqualTo(tokenB);

            String jtiA = jwtService.extractClaim(tokenA, Claims::getId);
            String jtiB = jwtService.extractClaim(tokenB, Claims::getId);
            assertThat(jtiA).isNotEqualTo(jtiB);

            assertThat(sha256Hex(tokenA)).isNotEqualTo(sha256Hex(tokenB));

            // Would have been identical pre-fix: same subject, same userId, same TOKEN_TYPE_REFRESH,
            // and iat/exp truncated to the same second for two calls this close together.
            assertThat(jwtService.extractMobile(tokenA)).isEqualTo(jwtService.extractMobile(tokenB));
        }

        @Test
        @DisplayName("N refresh tokens minted CONCURRENTLY for the same user are all pairwise unique (token string, jti, and hash)")
        void concurrentTokensNeverCollide() throws Exception {
            int n = 20;
            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch startGate = new CountDownLatch(1);
            try {
                List<Callable<String>> tasks = IntStream.range(0, n)
                        .<Callable<String>>mapToObj(i -> () -> {
                            startGate.await(5, TimeUnit.SECONDS);
                            return jwtService.generateRefreshToken(user);
                        })
                        .collect(Collectors.toList());

                List<Future<String>> futures = tasks.stream().map(pool::submit).collect(Collectors.toList());
                startGate.countDown(); // release all threads at once - maximizes same-instant collision odds
                List<String> tokens = new ArrayList<>();
                for (Future<String> f : futures) tokens.add(f.get(5, TimeUnit.SECONDS));

                assertThat(new HashSet<>(tokens)).as("all %d concurrently-issued tokens must be distinct", n).hasSize(n);

                Set<String> jtis = tokens.stream()
                        .map(t -> jwtService.extractClaim(t, Claims::getId))
                        .collect(Collectors.toSet());
                assertThat(jtis).as("all %d jti values must be distinct", n).hasSize(n);

                Set<String> hashes = tokens.stream().map(this::sha256Hex).collect(Collectors.toSet());
                assertThat(hashes).as("all %d stored hashes must be distinct - this is what actually prevents the "
                        + "single-use/revocation guarantee from breaking", n).hasSize(n);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("isTokenValid() / isTokenExpired()")
    class Validity {

        @Test
        @DisplayName("token for matching user, not expired → valid")
        void validForMatchingUser() {
            String token = jwtService.generateAccessToken(user);
            assertThat(jwtService.isTokenValid(token, user)).isTrue();
        }

        @Test
        @DisplayName("token for a different user → invalid")
        void invalidForDifferentUser() {
            String token = jwtService.generateAccessToken(user);
            User other = User.builder().mobile("9999999999").passwordHash("x")
                    .roles(Set.of()).build();
            assertThat(jwtService.isTokenValid(token, other)).isFalse();
        }

        @Test
        @DisplayName("already-expired token → parsing throws ExpiredJwtException")
        void expiredTokenThrowsOnParse() {
            ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1_000L);
            String token = jwtService.generateAccessToken(user);

            assertThatThrownBy(() -> jwtService.isTokenExpired(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }
    }
}
