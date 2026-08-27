package com.farm2home.auth.service;

import com.farm2home.auth.config.GoogleAuthProperties;
import com.farm2home.auth.exception.AuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GoogleIdTokenVerifier's real signature check requires reaching Google's public-key endpoint
 *  over the network - not exercised here (see AUTH_SOCIAL_OTP_PROGRESS.md's Live Verification
 *  section: "Google credential validated against a live Google token" is explicitly NOT claimed).
 *  What IS fully unit-testable without any network dependency, and covered below, is every
 *  fail-closed path: an unconfigured server, a missing credential, and a structurally invalid one
 *  (rejected during local JWT parsing, before any key fetch would occur). */
class GoogleTokenValidatorTest {

    private GoogleTokenValidator newValidator(String clientId) {
        GoogleAuthProperties properties = new GoogleAuthProperties();
        properties.setClientId(clientId);
        return new GoogleTokenValidator(properties);
    }

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("blank client id (Google Sign-In not configured) → fails closed, never attempts verification")
        void blankClientId_failsClosed() {
            GoogleTokenValidator validator = newValidator("");

            assertThatThrownBy(() -> validator.validate("anything"))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("null client id → fails closed")
        void nullClientId_failsClosed() {
            GoogleTokenValidator validator = newValidator(null);

            assertThatThrownBy(() -> validator.validate("anything"))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("blank credential → rejected before any verification attempt")
        void blankCredential_rejected() {
            GoogleTokenValidator validator = newValidator("configured-client-id.apps.googleusercontent.com");

            assertThatThrownBy(() -> validator.validate(""))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Missing Google credential");
        }

        @Test
        @DisplayName("null credential → rejected before any verification attempt")
        void nullCredential_rejected() {
            GoogleTokenValidator validator = newValidator("configured-client-id.apps.googleusercontent.com");

            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Missing Google credential");
        }

        @Test
        @DisplayName("structurally invalid credential (not a JWT) → rejected without leaking the underlying parse error")
        void malformedCredential_rejectedCleanly() {
            GoogleTokenValidator validator = newValidator("configured-client-id.apps.googleusercontent.com");

            assertThatThrownBy(() -> validator.validate("not-a-real-jwt"))
                    .isInstanceOf(AuthException.class)
                    .hasMessageNotContaining("Exception") // no raw stack/class name leaked
                    .satisfiesAnyOf(
                            ex -> org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("Google sign-in failed"),
                            ex -> org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("Invalid or expired")
                    );
        }
    }
}
