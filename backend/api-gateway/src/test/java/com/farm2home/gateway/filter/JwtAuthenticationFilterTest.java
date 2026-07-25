package com.farm2home.gateway.filter;

import com.farm2home.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("public paths")
    class PublicPaths {

        @Test
        @DisplayName("login path → passes through without checking the header")
        void loginPath_passesThrough() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/login"));

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
            verifyNoInteractions(jwtUtil);
        }
    }

    @Nested
    @DisplayName("protected paths")
    class ProtectedPaths {

        @Test
        @DisplayName("missing Authorization header → 401, chain never invoked")
        void missingHeader_unauthorized() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders"));

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("header without Bearer prefix → 401")
        void nonBearerHeader_unauthorized() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Basic xyz"));

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("invalid/expired token → 401")
        void invalidToken_unauthorized() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Bearer bad-token"));
            when(jwtUtil.validateAndExtractClaims("bad-token")).thenThrow(new JwtException("bad signature") {});

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("valid token → forwards request with X-User-* headers set from claims")
        void validToken_forwardsWithUserHeaders() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Bearer good-token"));

            Claims claims = mock(Claims.class);
            when(claims.get("userId", String.class)).thenReturn("11111111-1111-1111-1111-111111111111");
            when(claims.getSubject()).thenReturn("9876543210");
            when(claims.get("roles", List.class)).thenReturn(List.of("CUSTOMER", "SUPER_ADMIN"));
            when(jwtUtil.validateAndExtractClaims("good-token")).thenReturn(claims);

            filter.filter(exchange, chain).block();

            var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            ServerWebExchange mutated = captor.getValue();
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Id"))
                    .isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Mobile")).isEqualTo("9876543210");
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("CUSTOMER,SUPER_ADMIN");
        }
    }
}
