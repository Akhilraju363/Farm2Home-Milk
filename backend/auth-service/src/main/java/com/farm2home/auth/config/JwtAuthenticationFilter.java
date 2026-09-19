package com.farm2home.auth.config;

import com.farm2home.auth.service.JwtService;
import com.farm2home.auth.service.UserDetailsServiceImpl;
import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * SECURITY: a bearer token only ever authenticates a request when its {@code type} claim is
     * {@link SecurityConstants#TOKEN_TYPE_ACCESS} - a structurally valid, correctly-signed,
     * unexpired REFRESH token no longer authenticates like an access token (it used to, since
     * {@link JwtService#isTokenValid} only ever checked subject-match + expiry). Deliberately an
     * explicit positive match, not "not REFRESH": an unknown/missing {@code type} is treated as
     * insufficient, never as an implicit access grant (every access token this service has ever
     * issued has always set {@code type=ACCESS} - see JwtService.generateAccessToken - so no
     * previously-issued access token is affected by this).
     *
     * <p>On a type mismatch this method does NOT write a response or short-circuit the chain - it
     * simply leaves the request unauthenticated and lets {@code filterChain.doFilter} continue, the
     * same as when no bearer token is presented at all. Downstream, {@code SecurityConfig}'s
     * {@code anyRequest().authenticated()} rule (via its new {@code AuthenticationEntryPoint}) is
     * what turns "reached a protected endpoint unauthenticated" into a 401 - the filter itself never
     * decides that. This matters specifically for {@code POST /api/v1/auth/refresh-token}: it is a
     * {@code permitAll()} endpoint whose entire purpose is to receive a refresh token, so a design
     * that has this filter reject requests outright based on the presented type (as
     * {@code backend/app}'s {@code SpikeJwtAuthenticationFilter} does - safe there, since it never
     * accepts a refresh token in any form) would risk incorrectly blocking that endpoint. Not
     * authenticating and deferring the pass/fail decision to Spring Security's own per-endpoint
     * authorization rule is safe for every current and future {@code permitAll()} path by
     * construction, not merely because today's frontend happens not to trigger the bad case.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HeaderConstants.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());

        try {
            String mobile = jwtService.extractMobile(jwt);
            String tokenType = jwtService.extractClaim(jwt, c -> c.get(SecurityConstants.CLAIM_TYPE, String.class));
            boolean isAccessToken = SecurityConstants.TOKEN_TYPE_ACCESS.equals(tokenType);

            if (mobile != null && isAccessToken && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(mobile);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } else if (mobile != null && !isAccessToken) {
                log.warn("Rejecting bearer token with type '{}' (expected {}) for {} {}",
                        tokenType, SecurityConstants.TOKEN_TYPE_ACCESS, request.getMethod(), request.getRequestURI());
            }
        } catch (JwtException e) {
            log.warn("JWT validation error: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
