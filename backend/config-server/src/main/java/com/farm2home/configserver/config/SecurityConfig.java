package com.farm2home.configserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Same gap as discovery-service: without this, spring-boot-starter-security's browser-oriented
 * defaults enable CSRF protection, which would reject any non-GET call (e.g. the exposed
 * POST /actuator/refresh) with 401 despite correct Basic Auth credentials. Basic Auth is
 * stateless, so CSRF - a cookie/session attack mitigation - provides no protection here anyway.
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
                        // actuator endpoint (env, refresh, ...) stays behind Basic auth.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
