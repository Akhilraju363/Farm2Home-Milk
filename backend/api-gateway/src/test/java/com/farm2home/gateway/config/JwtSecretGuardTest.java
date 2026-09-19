package com.farm2home.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretGuardTest {

    @Test
    @DisplayName("blank secret under prod → refuses to start")
    void blankSecret_fails() {
        assertThatThrownBy(() -> new JwtSecretGuard("").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    @DisplayName("committed dev placeholder under prod → refuses to start")
    void devPlaceholder_fails() {
        assertThatThrownBy(() -> new JwtSecretGuard(JwtSecretGuard.COMMITTED_DEV_SECRET).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development placeholder");
    }

    @Test
    @DisplayName("a real unique secret → starts")
    void realSecret_ok() {
        assertThatCode(() -> new JwtSecretGuard(
                "b3VyLXJlYWwtcHJvZC1zZWNyZXQtd2hpY2gtaXMtcXVpdGUtbG9uZy1hbmQtcmFuZG9t").verify())
                .doesNotThrowAnyException();
    }
}
