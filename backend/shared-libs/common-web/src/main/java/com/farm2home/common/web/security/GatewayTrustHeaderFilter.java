package com.farm2home.common.web.security;

import com.farm2home.common.core.constants.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Runs before Spring Security. When gateway-origin enforcement is active
 * ({@code FARM2HOME_GATEWAY_INTERNAL_SECRET} set) and the request does <b>not</b> present the
 * shared secret, this filter hides every gateway-owned header ({@code X-User-Id},
 * {@code X-User-Mobile}, {@code X-User-Roles}, {@code X-Internal-Auth}) from the rest of the
 * chain. The per-service {@code GatewayHeaderAuthFilter} then sees no identity and establishes
 * no authentication, so a request that reached the service directly (bypassing the gateway)
 * cannot obtain privileges by forging those headers.
 *
 * <p>The request is never rejected outright - public endpoints (notably {@code /actuator/health},
 * which the platform's health check calls directly on the service) must stay reachable. Forged
 * identity is simply dropped.
 *
 * <p>When no secret is configured (local dev, CI, tests) this filter is a pass-through and
 * behaviour is identical to before it existed.
 */
public class GatewayTrustHeaderFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayTrustHeaderFilter.class);
    private static final Set<String> STRIPPED = Set.of(
            HeaderConstants.X_USER_ID.toLowerCase(),
            HeaderConstants.X_USER_MOBILE.toLowerCase(),
            HeaderConstants.X_USER_ROLES.toLowerCase(),
            HeaderConstants.X_INTERNAL_AUTH.toLowerCase());

    private final GatewayTrust gatewayTrust;

    public GatewayTrustHeaderFilter(GatewayTrust gatewayTrust) {
        this.gatewayTrust = gatewayTrust;
    }

    @Override
    public int getOrder() {
        // Before Spring Security's filter chain (registered at order -100 by Spring Boot).
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (gatewayTrust.isEnforcing() && !gatewayTrust.isTrusted(request)) {
            if (request.getHeader(HeaderConstants.X_USER_ID) != null) {
                log.warn("Dropped forged gateway identity headers on a request that did not come "
                        + "through the api-gateway: {} {}", request.getMethod(), request.getRequestURI());
            }
            filterChain.doFilter(new StrippedIdentityRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Hides the gateway-owned headers so nothing downstream can read a forged value. */
    private static final class StrippedIdentityRequest extends HttpServletRequestWrapper {
        StrippedIdentityRequest(HttpServletRequest request) {
            super(request);
        }

        private boolean stripped(String name) {
            return name != null && STRIPPED.contains(name.toLowerCase());
        }

        @Override
        public String getHeader(String name) {
            return stripped(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return stripped(name) ? Collections.enumeration(List.of()) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(this::stripped);
            return Collections.enumeration(names);
        }
    }
}
