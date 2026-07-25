package com.farm2home.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    // Same well-formed 256-bit+ base64 test secret used elsewhere in this repo's default config.
    private static final String SECRET =
            "ZmFybTJob21lLXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String tokenExpiringIn(long millis) {
        return Jwts.builder()
                .subject("9876543210")
                .claim("userId", "11111111-1111-1111-1111-111111111111")
                .claim("roles", List.of("CUSTOMER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + millis))
                .signWith(signingKey())
                .compact();
    }

    @Nested
    @DisplayName("validateAndExtractClaims()")
    class ValidateAndExtractClaims {

        @Test
        @DisplayName("valid, unexpired token → returns claims")
        void validToken_returnsClaims() {
            String token = tokenExpiringIn(60_000);

            Claims claims = jwtUtil.validateAndExtractClaims(token);

            assertThat(claims.getSubject()).isEqualTo("9876543210");
            assertThat(claims.get("userId", String.class)).isEqualTo("11111111-1111-1111-1111-111111111111");
        }

        @Test
        @DisplayName("expired token → throws ExpiredJwtException")
        void expiredToken_throws() {
            String token = tokenExpiringIn(-60_000);

            assertThatThrownBy(() -> jwtUtil.validateAndExtractClaims(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("token signed with a different key → throws SignatureException")
        void wrongSignature_throws() {
            SecretKey otherKey = Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode("YW5vdGhlci1kaWZmZXJlbnQtc2VjcmV0LWtleS1hdC1sZWFzdC0yNTYtYml0cw=="));
            String token = Jwts.builder()
                    .subject("9876543210")
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(otherKey)
                    .compact();

            assertThatThrownBy(() -> jwtUtil.validateAndExtractClaims(token))
                    .isInstanceOf(SignatureException.class);
        }

        @Test
        @DisplayName("malformed token string → throws")
        void malformedToken_throws() {
            assertThatThrownBy(() -> jwtUtil.validateAndExtractClaims("not-a-valid-jwt"))
                    .isInstanceOf(io.jsonwebtoken.JwtException.class);
        }
    }
}
