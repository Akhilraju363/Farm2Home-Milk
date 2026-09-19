package com.farm2home.app.security;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servlet authentication for the single-JVM aggregation. The api-gateway is not running here, so
 * this filter does the job the gateway's reactive {@code JwtAuthenticationFilter} normally does.
 *
 * <p>Three mutually-exclusive outcomes per request:
 *
 * <ol>
 *   <li><b>{@code Authorization: Bearer <jwt>} present</b> — verify the HMAC signature + expiry
 *       with the shared {@code jwt.secret}, require {@code type == ACCESS} (a REFRESH token is
 *       rejected 401), establish the principal from the token's own claims
 *       ({@code userId} / {@code sub} / {@code roles}). Any client-supplied {@code X-User-*} /
 *       {@code X-Internal-Auth} headers are hidden from everything downstream.</li>
 *   <li><b>No bearer, but the request presents the per-process {@code X-Internal-Auth} secret
 *       ({@link InternalCallToken}) AND arrived over the loopback interface AND carries
 *       {@code X-User-Id}/{@code X-User-Roles}</b> — trust that fixed system identity and build
 *       the principal from it. This is what authenticates the in-process {@code lb://} calls
 *       made by <i>invoice-service</i> and <i>notification-service</i>, whose clients forward a
 *       fixed {@code SUPER_ADMIN} system identity via {@code X-User-*} headers (not a bearer)
 *       because the original caller's role may be too narrow for the downstream read. The
 *       {@code X-Internal-Auth} secret is the single-JVM analogue of the api-gateway's
 *       proof-of-origin secret ({@code common-web}'s {@code GatewayTrust}): it is generated
 *       fresh per process, never leaves the container, is stamped only on rewritten {@code lb://}
 *       requests by {@link com.farm2home.app.config.LoadBalancerClientConfig}, and is
 *       constant-time compared here. The loopback-peer check ({@code request.getRemoteAddr()} is
 *       127.0.0.1 / ::1 — the real TCP peer, not a spoofable header; forward-headers processing
 *       is deliberately left off) is defence in depth on top of the secret. A forged
 *       {@code X-User-*} from an external client — or any local client that does not know the
 *       secret — cannot authenticate.</li>
 *   <li><b>Neither</b> — the request continues unauthenticated with all {@code X-User-*} /
 *       {@code X-Internal-Auth} stripped, and Spring Security's 401 entry point handles anything
 *       that requires authentication.</li>
 * </ol>
 *
 * <p>Authorities are granted <b>both</b> bare ({@code FARM_MANAGER}) and role-prefixed
 * ({@code ROLE_FARM_MANAGER}) so both {@code hasAnyRole(...)} (farm, invoice) and
 * {@code hasAnyAuthority(...)} (everyone else) keep working under one chain.
 */
public class SpikeJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SpikeJwtAuthenticationFilter.class);

    private static final Set<String> STRIPPED_HEADERS;
    static {
        Set<String> s = new LinkedHashSet<>();
        for (String h : HeaderConstants.GATEWAY_OWNED_HEADERS) {
            s.add(h.toLowerCase());
        }
        STRIPPED_HEADERS = Collections.unmodifiableSet(s);
    }

    private final SecretKey signingKey;
    private final InternalCallToken internalCallToken;

    public SpikeJwtAuthenticationFilter(String base64Secret, InternalCallToken internalCallToken) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.internalCallToken = internalCallToken;
    }

    /**
     * Also run on the ASYNC dispatch. Spring MVC returns a {@code StreamingResponseBody}
     * (reports-service's CSV/Excel/PDF exports) immediately and then re-dispatches through the
     * whole filter chain once the body has been written. {@link SpikeJwtAuthenticationFilter}
     * establishes the {@link SpikePrincipal} straight into the {@code SecurityContextHolder}
     * without persisting it to a {@code SecurityContextRepository} (there is no session — this is
     * stateless), so on the async re-dispatch the context is empty and Spring Security's
     * {@code AuthorizationFilter} would raise {@code AccessDeniedException} (harmless post-commit
     * — the client already has the full file — but it spams ERROR-level stack traces on every
     * export). Re-running this cheap filter (a JWT parse) on the async dispatch re-establishes the
     * identity so the re-authorization passes cleanly. Non-async requests are unaffected.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HeaderConstants.AUTHORIZATION);
        HttpServletRequest sanitized = new StrippedIdentityRequest(request);

        if (authHeader != null && authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            String token = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
            try {
                Claims claims = Jwts.parser().verifyWith(signingKey).build()
                        .parseSignedClaims(token).getPayload();

                String tokenType = claims.get(SecurityConstants.CLAIM_TYPE, String.class);
                if (tokenType != null && !SecurityConstants.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                    unauthorized(response, "Invalid or expired token");
                    return;
                }
                String userId = claims.get(SecurityConstants.CLAIM_USER_ID, String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
                authenticate(userId, claims.getSubject(), roles == null ? List.of() : roles);
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("JWT validation failed for {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.getMessage());
                unauthorized(response, "Invalid or expired token");
                return;
            }
        } else if (internalCallToken.matches(request.getHeader(HeaderConstants.X_INTERNAL_AUTH))
                && isLoopback(request)) {
            String hdrUserId = request.getHeader(HeaderConstants.X_USER_ID);
            String hdrRoles = request.getHeader(HeaderConstants.X_USER_ROLES);
            if (StringUtils.hasText(hdrUserId) && StringUtils.hasText(hdrRoles)) {
                List<String> roles = Arrays.stream(hdrRoles.split(","))
                        .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toList());
                authenticate(hdrUserId, request.getHeader(HeaderConstants.X_USER_MOBILE), roles);
            }
        }
        // else: unauthenticated — a forged X-User-* (no bearer, no valid X-Internal-Auth secret)
        // stays hidden via `sanitized` and Spring Security's 401 entry point handles it.

        filterChain.doFilter(sanitized, response);
    }

    private void authenticate(String userId, String mobile, List<String> roles) {
        Set<String> roleSet = new LinkedHashSet<>(roles);
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String r : roleSet) {
            authorities.add(new SimpleGrantedAuthority(r));
            authorities.add(new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + r));
        }
        UUID uuid = null;
        if (StringUtils.hasText(userId)) {
            try {
                uuid = UUID.fromString(userId);
            } catch (IllegalArgumentException ignored) {
                // non-UUID userId (shouldn't happen) — leave null, principal still usable
            }
        }
        SpikePrincipal principal = new SpikePrincipal(uuid, mobile, roleSet);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private static boolean isLoopback(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }

    /** Hides gateway-owned identity headers from everything downstream of this filter. */
    private static final class StrippedIdentityRequest extends HttpServletRequestWrapper {
        StrippedIdentityRequest(HttpServletRequest request) {
            super(request);
        }

        private boolean stripped(String name) {
            return name != null && STRIPPED_HEADERS.contains(name.toLowerCase());
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
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            names.removeIf(this::stripped);
            return Collections.enumeration(names);
        }
    }
}
