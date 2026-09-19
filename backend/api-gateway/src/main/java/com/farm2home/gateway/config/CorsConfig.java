package com.farm2home.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The single source of truth for CORS. The old {@code spring.cloud.gateway.globalcors} block in
 * application.yml has been removed so there is exactly one place to reason about.
 *
 * <p>Allowed origins come from {@code FARM2HOME_FRONTEND_ORIGINS} (comma-separated), defaulting
 * to the local Vite dev servers. Production sets it to the real frontend origin(s), e.g.
 * {@code https://farm2home.vercel.app}. A wildcard origin is never used together with
 * {@code allowCredentials=true}: exact origins are matched and echoed back.
 */
@Configuration
public class CorsConfig {

    @Value("${farm2home.frontend.origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    CorsConfiguration buildCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildCorsConfiguration());
        return new CorsWebFilter(source);
    }
}
