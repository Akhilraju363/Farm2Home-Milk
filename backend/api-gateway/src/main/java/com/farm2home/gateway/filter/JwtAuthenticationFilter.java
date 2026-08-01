package com.farm2home.gateway.filter;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        "/swagger-ui",
        "/api-docs",
        "/actuator/health",
        "/uploads"
    );

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HeaderConstants.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());

        try {
            Claims claims = jwtUtil.validateAndExtractClaims(token);

            String userId = claims.get(SecurityConstants.CLAIM_USER_ID, String.class);
            String mobile = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
            String rolesHeader = roles != null ? String.join(",", roles) : "";

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(HeaderConstants.X_USER_ID, userId != null ? userId : "")
                    .header(HeaderConstants.X_USER_MOBILE, mobile != null ? mobile : "")
                    .header(HeaderConstants.X_USER_ROLES, rolesHeader)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

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
