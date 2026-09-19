package com.farm2home.app.security;

import com.farm2home.common.core.constants.ApiConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Stream;

/**
 * The single application-level {@code SecurityFilterChain} for the aggregation.
 *
 * <p>No service's {@code config.SecurityConfig} is component-scanned, so exactly one chain and
 * one {@code @EnableWebSecurity}/{@code @EnableMethodSecurity} exist here. The 13 service
 * SecurityConfig classes are structurally identical (CSRF off, STATELESS, permitAll(PUBLIC),
 * {@code anyRequest().authenticated()}, add a header/JWT filter, {@code @EnableMethodSecurity})
 * — the real per-endpoint rules are the {@code @PreAuthorize} annotations on the controllers,
 * which keep enforcing here because those controllers ARE scanned and {@code @EnableMethodSecurity}
 * is on.
 *
 * <h2>Public (permitAll) set — union of every embedded service's own list</h2>
 * <ul>
 *   <li>{@link ApiConstants#PUBLIC_ENDPOINTS_BASE} — health/info + api-docs/swagger (all services)</li>
 *   <li>{@code /uploads/**} — customer, inventory, farm (static file serving)</li>
 *   <li>{@code POST /api/v1/data-rights-requests/submit} — customer-service's DPDP intake, which
 *       a data principal with no account must be able to reach (method-specific, as in
 *       customer's own SecurityConfig)</li>
 *   <li>{@code POST /api/v1/payments/webhook} — payment-service's server-to-server gateway
 *       webhook; its payload HMAC signature is verified inside {@code PaymentServiceImpl}. The
 *       legacy {@code POST /payments/callback} is deliberately NOT public (admin token +
 *       {@code farm2home.payment.legacy-callback-enabled}, which stays false).</li>
 * </ul>
 * Auth-service's public {@code /api/v1/auth/**} paths are added when Group 7 embeds auth.
 *
 * <p>{@link SpikeJwtAuthenticationFilter} grants both {@code ROLE_}-prefixed and bare
 * authorities per role, so both {@code hasAnyRole(...)} (farm, invoice) and
 * {@code hasAnyAuthority(...)} (everyone else) keep working under one chain — no rule change.
 *
 * <p>Refinement over the originals: a 401 (not Spring's default 403) entry point for
 * unauthenticated requests, matching the api-gateway. Not an authorization-rule change.
 *
 * <h2>CORS</h2>
 * No service's own CORS config is scanned (none existed to begin with — every service relied on
 * the api-gateway's central {@code CorsConfig}, which does not exist in this deployment). This
 * class now owns CORS directly: origins come from {@code CORS_ALLOWED_ORIGINS} (comma-separated),
 * defaulting to the local Vite dev origins. {@code Content-Disposition} is exposed for the
 * reports-service CSV/Excel/PDF export downloads, mirroring the api-gateway's allowlist.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SpikeSecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = Stream.concat(
            Stream.of(ApiConstants.PUBLIC_ENDPOINTS_BASE),
            Stream.of("/uploads/**")
    ).toArray(String[]::new);

    private final String jwtSecret;
    private final List<String> corsAllowedOrigins;

    public SpikeSecurityConfig(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${farm2home.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
            List<String> corsAllowedOrigins) {
        this.jwtSecret = jwtSecret;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SpikeJwtAuthenticationFilter spikeJwtAuthenticationFilter(InternalCallToken internalCallToken) {
        return new SpikeJwtAuthenticationFilter(jwtSecret, internalCallToken);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SpikeJwtAuthenticationFilter jwtFilter,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // customer-service: DPDP grievance/data-rights intake — narrow /submit
                        // sub-path only (the GET list + PATCH triage on the same base path stay
                        // SUPER_ADMIN via their own @PreAuthorize).
                        .requestMatchers(HttpMethod.POST, "/api/v1/data-rights-requests/submit").permitAll()
                        // payment-service: gateway server-to-server webhook (HMAC-verified in the handler).
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        };
    }
}
