package com.farm2home.auth.config;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.RoleType;
import com.farm2home.auth.service.JwtService;
import com.farm2home.auth.service.UserDetailsServiceImpl;
import com.farm2home.common.core.constants.HeaderConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * REGRESSION for the refresh-token-as-bearer-access-token security gap: {@code JwtAuthenticationFilter}
 * used to authenticate any structurally valid, unexpired bearer token regardless of its {@code type}
 * claim (its {@code isTokenValid} check only ever compared subject + expiry) - live-verified before
 * this fix: {@code Authorization: Bearer <refresh-token>} against {@code GET /api/v1/auth/me}
 * returned 200. This exercises the filter directly (a REAL {@link JwtService}, so tokens carry real
 * claims, with {@link UserDetailsServiceImpl} mocked) rather than going through a full
 * {@code @SpringBootTest} - {@code AuthControllerTest} deliberately disables filters
 * ({@code addFilters = false}) for its own controller-slice purposes, and the only full-context test
 * in this module ({@code AuthServiceApplicationTests}) is Testcontainers-gated (broken in this dev
 * environment - a docker-java/API-version mismatch, unrelated to this fix). The actual end-to-end
 * HTTP status codes (401 vs 403, via the new {@code AuthenticationEntryPoint} in
 * {@code SecurityConfig}, which this unit-level test cannot exercise) were live-verified against a
 * real running instance - see docs/PRODUCTION_READINESS.md.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "ZmFybTJob21lLXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private JwtService jwtService;
    private UserDetailsServiceImpl userDetailsService;
    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604_800_000L);

        userDetailsService = mock(UserDetailsServiceImpl.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

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

        lenient().when(userDetailsService.loadUserByUsername(user.getMobile())).thenReturn(user);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse runFilter(String authorizationHeaderValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeaderValue != null) {
            request.addHeader(HeaderConstants.AUTHORIZATION, authorizationHeaderValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }

    @Nested
    @DisplayName("REGRESSION: only an ACCESS-type token authenticates")
    class TokenTypeDistinction {

        @Test
        @DisplayName("A: a valid access token authenticates as the matching user")
        void accessTokenAuthenticates() throws Exception {
            String token = jwtService.generateAccessToken(user);

            runFilter("Bearer " + token);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(user);
        }

        @Test
        @DisplayName("B: a valid, correctly-signed, unexpired REFRESH token does NOT authenticate - this was the bug")
        void refreshTokenDoesNotAuthenticate() throws Exception {
            String token = jwtService.generateRefreshToken(user);

            MockHttpServletResponse response = runFilter("Bearer " + token);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            // Not rejected outright either (see the filter's javadoc) - the chain continues so a
            // request to a permitAll() endpoint (e.g. /auth/refresh-token) is never blocked by this.
            assertThat(response.getStatus()).isEqualTo(200);
            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("no Authorization header - unchanged no-op behavior")
        void noHeader_isNoOp() throws Exception {
            MockHttpServletResponse response = runFilter(null);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("G: expired refresh token does not authenticate (rejected during parsing, same as any expired token)")
        void expiredRefreshToken_rejected() throws Exception {
            ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", -1_000L);
            String token = jwtService.generateRefreshToken(user);

            MockHttpServletResponse response = runFilter("Bearer " + token);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("F: expired access token -> rejected (401), not authenticated")
        void expiredAccessToken_rejected() throws Exception {
            ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1_000L);
            String token = jwtService.generateAccessToken(user);

            MockHttpServletResponse response = runFilter("Bearer " + token);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("H: malformed token -> 401, chain short-circuited")
        void malformedToken_rejected() throws Exception {
            MockHttpServletResponse response = runFilter("Bearer not-a-jwt-at-all");

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("I: wrong-signature token -> 401")
        void wrongSignature_rejected() throws Exception {
            String token = jwtService.generateAccessToken(user);
            String tampered = token.substring(0, token.length() - 4) + "Xaaa";

            MockHttpServletResponse response = runFilter("Bearer " + tampered);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("a token with an unrecognized/garbage type claim does not authenticate either (positive match required, not 'not REFRESH')")
        void unknownTokenType_doesNotAuthenticate() throws Exception {
            // Minted directly (not via JwtService) specifically to carry an unexpected type value -
            // proves the filter requires an explicit ACCESS match rather than merely excluding REFRESH.
            String token = io.jsonwebtoken.Jwts.builder()
                    .subject(user.getMobile())
                    .claim("userId", user.getId().toString())
                    .claim("type", "SOMETHING_ELSE")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000L))
                    .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            io.jsonwebtoken.io.Decoders.BASE64.decode(SECRET)))
                    .compact();

            runFilter("Bearer " + token);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
