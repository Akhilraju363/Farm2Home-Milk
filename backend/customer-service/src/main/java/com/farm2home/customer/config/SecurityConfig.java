package com.farm2home.customer.config;

import com.farm2home.common.core.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = Stream.concat(
            Stream.of(ApiConstants.PUBLIC_ENDPOINTS_BASE),
            Stream.of("/uploads/**")
    ).toArray(String[]::new);

    private final GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // DPDP grievance/data-rights channel must be reachable without an account. Kept
                        // on its own /submit sub-path (not the base /data-rights-requests path used by
                        // GET/PATCH) because the gateway's own JwtAuthenticationFilter matches public
                        // paths by startsWith with no per-HTTP-method distinction - a shared prefix would
                        // have made the admin list/triage endpoints public too. See JwtAuthenticationFilter
                        // PUBLIC_PATHS, which must list this same /submit path.
                        // NOTE: unlike every other public path here, this has no CAPTCHA/rate-limit in
                        // front of it yet - see DPDP_PROGRESS.md "Security gaps flagged".
                        .requestMatchers(HttpMethod.POST, "/api/v1/data-rights-requests/submit").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
