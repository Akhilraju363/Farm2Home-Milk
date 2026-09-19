package com.farm2home.gateway.filter;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    // Gateway matches these by startsWith (not Ant-pattern like the services' own
    // SecurityConfigs), so this list intentionally isn't built from
    // ApiConstants.PUBLIC_ENDPOINTS_BASE - "/actuator/**" as a literal prefix would never
    // match "/actuator/health" under startsWith semantics.
    private static final List<String> PUBLIC_PATHS = List.of(
        ApiConstants.API_V1 + "/auth/register",
        ApiConstants.API_V1 + "/auth/login",
        ApiConstants.API_V1 + "/auth/send-otp",
        ApiConstants.API_V1 + "/auth/verify-otp",
        ApiConstants.API_V1 + "/auth/refresh-token",
        ApiConstants.API_V1 + "/auth/google",
        // Razorpay calls this directly and cannot attach our JWT - safe to expose because the
        // payload's own HMAC-SHA256 signature (RazorpaySignature, checked before anything else
        // in PaymentServiceImpl.handleWebhook) is the real authentication here, not JWT.
        // Deliberately NOT adding /payments/callback alongside it: that endpoint performs no
        // signature/secret verification at all (see PaymentController's own doc comment on it -
        // "manual/legacy... to simulate gateway outcomes under the mock provider, local
        // dev/tests") and updates a payment purely from a guessable paymentReference. Making it
        // public here would let anyone on the internet flip any payment to SUCCESS/FAILED with
        // zero authentication. It stays behind the gateway's normal JWT requirement (any
        // authenticated caller can still reach it, same as before this fix - only truly
        // anonymous access is what's newly blocked here for /webhook and only /webhook).
        ApiConstants.API_V1 + "/payments/webhook",
        // DPDP data-rights/grievance intake - must be reachable by a data principal with no
        // account. Deliberately the narrow /submit sub-path, not the base /data-rights-requests
        // path also used by the admin list (GET) and triage (PATCH) endpoints, since this list
        // matches by startsWith with no per-HTTP-method distinction.
        ApiConstants.API_V1 + "/data-rights-requests/submit",
        "/swagger-ui",
        "/api-docs",
        "/actuator/health",
        "/uploads"
    );

    private final JwtUtil jwtUtil;

    /** Shared secret proving a request originated here. Blank in local/dev - downstream
     *  services then run with gateway-origin enforcement disabled (unchanged behaviour). */
    @Value("${farm2home.gateway.internal-secret:}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // A client must never be able to supply these - strip every inbound copy up front,
        // on every path (public included), before we (maybe) set our own.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    for (String owned : HeaderConstants.GATEWAY_OWNED_HEADERS) {
                        h.remove(owned);
                    }
                })
                .build();

        if (isPublicPath(path)) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authHeader = sanitized.getHeaders().getFirst(HeaderConstants.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());

        try {
            Claims claims = jwtUtil.validateAndExtractClaims(token);

            // Only access tokens authenticate an API call; a refresh token must not.
            String tokenType = claims.get(SecurityConstants.CLAIM_TYPE, String.class);
            if (tokenType != null && !SecurityConstants.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                return unauthorizedResponse(exchange, "Invalid or expired token");
            }

            String userId = claims.get(SecurityConstants.CLAIM_USER_ID, String.class);
            String mobile = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
            String rolesHeader = roles != null ? String.join(",", roles) : "";

            ServerHttpRequest.Builder mutated = sanitized.mutate()
                    .header(HeaderConstants.X_USER_ID, userId != null ? userId : "")
                    .header(HeaderConstants.X_USER_MOBILE, mobile != null ? mobile : "")
                    .header(HeaderConstants.X_USER_ROLES, rolesHeader);
            if (internalSecret != null && !internalSecret.isBlank()) {
                mutated.header(HeaderConstants.X_INTERNAL_AUTH, internalSecret);
            }

            return chain.filter(exchange.mutate().request(mutated.build()).build());

        } catch (JwtException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}", message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
