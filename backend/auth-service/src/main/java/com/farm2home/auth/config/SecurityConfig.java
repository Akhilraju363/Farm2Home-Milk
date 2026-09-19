package com.farm2home.auth.config;

import com.farm2home.auth.service.UserDetailsServiceImpl;
import com.farm2home.common.core.constants.ApiConstants;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    // Auth-specific public business endpoints, on top of the shared actuator/docs base.
    private static final String[] PUBLIC_ENDPOINTS = Stream.concat(
            Stream.of(ApiConstants.PUBLIC_ENDPOINTS_BASE),
            Stream.of(
                    ApiConstants.API_V1 + "/auth/register",
                    ApiConstants.API_V1 + "/auth/login",
                    ApiConstants.API_V1 + "/auth/send-otp",
                    ApiConstants.API_V1 + "/auth/verify-otp",
                    ApiConstants.API_V1 + "/auth/refresh-token",
                    ApiConstants.API_V1 + "/auth/google"
            )
    ).toArray(String[]::new);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // No gateway in the Render deployment — the frontend calls auth-service directly,
                // so this service answers its own CORS preflight. See CorsConfig.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // A JWT presented with the wrong token type (e.g. a refresh token on a protected
                // endpoint like /me) is deliberately left unauthenticated by JwtAuthenticationFilter
                // rather than rejected there directly (see that filter's javadoc for why) - this is
                // what turns that, and a completely missing Authorization header, into a 401
                // (Spring Security's own default here would be 403, matching backend/app's existing
                // SpikeSecurityConfig entry point for consistency).
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedEntryPoint()))
                .build();
    }

    private static AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
