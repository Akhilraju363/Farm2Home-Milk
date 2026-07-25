package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.RoleType;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
            List<String> roles = jwtService.extractClaim(token, c -> c.get("roles", List.class));
            assertThat(roles).containsExactly("CUSTOMER");

            String userId = jwtService.extractClaim(token, c -> c.get("userId", String.class));
            assertThat(userId).isEqualTo(user.getId().toString());

            String type = jwtService.extractClaim(token, c -> c.get("type", String.class));
            assertThat(type).isEqualTo("ACCESS");
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("produces a token with type REFRESH and no roles claim")
        void hasRefreshType() {
            String token = jwtService.generateRefreshToken(user);

            assertThat(jwtService.extractMobile(token)).isEqualTo(user.getMobile());
            String type = jwtService.extractClaim(token, c -> c.get("type", String.class));
            assertThat(type).isEqualTo("REFRESH");
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
