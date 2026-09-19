package com.farm2home.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * CORS for auth-service's own {@code SecurityFilterChain}.
 *
 * <p>In the microservice/docker-compose deployment this was unnecessary — the api-gateway was the
 * only browser-facing ingress and owned CORS centrally. In the $0 Render architecture there is no
 * gateway: the frontend (Vercel) calls auth-service directly, so this service must answer its own
 * CORS preflight and echo its own {@code Access-Control-Allow-Origin}.
 *
 * <p>Origins come from {@code CORS_ALLOWED_ORIGINS} (comma-separated), defaulting to the local
 * Vite dev origins so local/docker-compose behaviour is unchanged. A wildcard is never combined
 * with {@code allowCredentials=true} — exact origins are matched and echoed back, mirroring the
 * api-gateway's {@code CorsConfig} and {@code backend/app}'s {@code SpikeSecurityConfig}.
 */
@Configuration
public class CorsConfig {

    @Value("${farm2home.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
