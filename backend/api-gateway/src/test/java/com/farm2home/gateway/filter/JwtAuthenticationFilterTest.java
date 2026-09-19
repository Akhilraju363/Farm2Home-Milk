package com.farm2home.gateway.filter;

import com.farm2home.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

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

    private ServerWebExchange forwarded() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("public paths")
    class PublicPaths {

        @Test
        @DisplayName("login path → passes through without checking the token")
        void loginPath_passesThrough() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/login"));

            filter.filter(exchange, chain).block();

            verify(chain).filter(any());
            verifyNoInteractions(jwtUtil);
        }

        @Test
        @DisplayName("forged X-User-* / X-Internal-Auth on a public path are stripped before forwarding")
        void publicPath_stripsForgedIdentity() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                            .header("X-User-Roles", "SUPER_ADMIN")
                            .header("X-Internal-Auth", "guessed"));

            filter.filter(exchange, chain).block();

            var headers = forwarded().getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isNull();
            assertThat(headers.getFirst("X-User-Roles")).isNull();
            assertThat(headers.getFirst("X-Internal-Auth")).isNull();
        }

        @Test
        @DisplayName("payments webhook path → passes through with no token")
        void paymentsWebhookPath_passesThroughWithoutToken() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/payments/webhook"));

            filter.filter(exchange, chain).block();

            verify(chain).filter(any());
            verifyNoInteractions(jwtUtil);
        }
    }

    @Nested
    @DisplayName("payments callback path")
    class PaymentsCallbackPath {

        @Test
        @DisplayName("callback path → still requires a token (no signature verification of its own)")
        void callbackPath_stillRequiresToken() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/payments/callback"));

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
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
        @DisplayName("forged X-User-Roles with NO valid token → 401 (headers never trusted)")
        void forgedRolesNoToken_unauthorized() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders")
                            .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                            .header("X-User-Mobile", "9876543210")
                            .header("X-User-Roles", "SUPER_ADMIN"));

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
        @DisplayName("refresh token used as access token → 401")
        void refreshToken_unauthorized() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Bearer refresh-token"));
            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("REFRESH");
            when(jwtUtil.validateAndExtractClaims("refresh-token")).thenReturn(claims);

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("valid token → identity comes from claims, inbound forged headers overridden, internal-auth added")
        void validToken_identityFromClaims_notInboundHeaders() {
            ReflectionTestUtils.setField(filter, "internalSecret", "the-shared-secret");
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders")
                            .header("Authorization", "Bearer good-token")
                            // attacker also tries to smuggle elevated identity in headers:
                            .header("X-User-Roles", "SUPER_ADMIN")
                            .header("X-Internal-Auth", "guessed"));

            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("ACCESS");
            when(claims.get("userId", String.class)).thenReturn("11111111-1111-1111-1111-111111111111");
            when(claims.getSubject()).thenReturn("9876543210");
            when(claims.get("roles", List.class)).thenReturn(List.of("CUSTOMER"));
            when(jwtUtil.validateAndExtractClaims("good-token")).thenReturn(claims);

            filter.filter(exchange, chain).block();

            var headers = forwarded().getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(headers.getFirst("X-User-Mobile")).isEqualTo("9876543210");
            assertThat(headers.getFirst("X-User-Roles")).isEqualTo("CUSTOMER"); // not SUPER_ADMIN
            assertThat(headers.getFirst("X-Internal-Auth")).isEqualTo("the-shared-secret");
        }
    }
}
