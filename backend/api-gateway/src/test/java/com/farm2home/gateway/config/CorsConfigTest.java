package com.farm2home.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfiguration configWithOrigins(List<String> origins) {
        CorsConfig cors = new CorsConfig();
        ReflectionTestUtils.setField(cors, "allowedOrigins", origins);
        return cors.buildCorsConfiguration();
    }

    @Test
    @DisplayName("production origin allowed; unknown origin rejected; no wildcard")
    void productionOrigins_onlyConfiguredAllowed() {
        CorsConfiguration config = configWithOrigins(List.of("https://farm2home.vercel.app"));

        assertThat(config.getAllowedOrigins()).containsExactly("https://farm2home.vercel.app");
        assertThat(config.getAllowedOrigins()).doesNotContain("*");
        assertThat(config.getAllowedOriginPatterns()).isNull();
        assertThat(config.checkOrigin("https://farm2home.vercel.app")).isEqualTo("https://farm2home.vercel.app");
        assertThat(config.checkOrigin("https://evil.example.com")).isNull();
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("local dev default origins")
    void localDefaults() {
        CorsConfiguration config = configWithOrigins(List.of("http://localhost:3000", "http://localhost:5173"));

        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(config.checkOrigin("http://localhost:9999")).isNull();
    }
}
