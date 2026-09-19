package com.farm2home.discovery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Without this, spring-boot-starter-security falls back to its browser-oriented defaults,
 * which enable CSRF protection. The Eureka Java client never sends a CSRF token, so every
 * registration/heartbeat/deregistration call (POST/PUT/DELETE) was silently rejected with 401
 * while plain GETs (health checks, browsing the registry) worked fine with the exact same
 * credentials - masking the real cause. Basic Auth is inherently stateless, so CSRF (a
 * cookie/session attack mitigation) provides no protection here anyway.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Only the liveness/readiness probe is unauthenticated; every other
                        // actuator endpoint (env, metrics, ...) stays behind Basic auth.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
