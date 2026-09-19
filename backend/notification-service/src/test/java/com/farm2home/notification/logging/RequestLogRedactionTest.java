package com.farm2home.notification.logging;

import com.farm2home.observability.web.ReactiveRequestTraceIdFilter;
import com.farm2home.observability.web.RequestTraceIdFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lives here rather than in common-observability, which has no test setup and whose JaCoCo gate (80% lines,
 * enforced at verify) would start failing on its untested health indicators the moment it gained a test.
 * These exercise the real shared filter classes from that jar - the servlet one is active in this service and
 * the reactive one is the api-gateway's.
 *
 * <p>The request-trace filters log every request header at INFO. Regression for a real finding: they
 * redacted only authorization/cookie/set-cookie, so the shared internal secret
 * ({@code X-Internal-Auth}, the credential proving a request came from the gateway) was written to the log of every
 * service on every internal call.
 */
class RequestLogRedactionTest {

    private static final String SECRET = "super-secret-internal-value-do-not-log-0123456789";
    private static final String BEARER = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature";

    private ListAppender<ILoggingEvent> servletLogs;
    private ListAppender<ILoggingEvent> reactiveLogs;
    private Logger servletLogger;
    private Logger reactiveLogger;

    @BeforeEach
    void attach() {
        servletLogger = (Logger) LoggerFactory.getLogger(RequestTraceIdFilter.class);
        reactiveLogger = (Logger) LoggerFactory.getLogger(ReactiveRequestTraceIdFilter.class);
        servletLogs = new ListAppender<>();
        reactiveLogs = new ListAppender<>();
        servletLogs.start();
        reactiveLogs.start();
        servletLogger.addAppender(servletLogs);
        reactiveLogger.addAppender(reactiveLogs);
    }

    @AfterEach
    void detach() {
        servletLogger.detachAppender(servletLogs);
        reactiveLogger.detachAppender(reactiveLogs);
    }

    private static String joined(ListAppender<ILoggingEvent> appender) {
        StringBuilder sb = new StringBuilder();
        appender.list.forEach(e -> sb.append(e.getFormattedMessage()).append('\n'));
        return sb.toString();
    }

    @Test
    @DisplayName("servlet filter: X-Internal-Auth / Authorization / Cookie are never logged; ordinary headers still are")
    void servletFilterRedactsSecrets() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("X-Internal-Auth", SECRET);
        request.addHeader("Authorization", BEARER);
        request.addHeader("Cookie", "session=abc123");
        request.addHeader("X-User-Roles", "CUSTOMER");

        new RequestTraceIdFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        String logged = joined(servletLogs);
        assertThat(logged).contains("Incoming request POST /api/v1/orders")
                .contains("X-User-Roles=CUSTOMER");                                   // logging still useful
        assertThat(logged).doesNotContain(SECRET).doesNotContain("X-Internal-Auth").doesNotContain("x-internal-auth")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9").doesNotContain("abc123");
    }

    @Test
    @DisplayName("servlet filter: header-name casing cannot bypass the redaction")
    void servletFilterRedactionIsCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("X-INTERNAL-AUTH", SECRET);
        request.addHeader("x-internal-auth", SECRET + "-second");

        new RequestTraceIdFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(joined(servletLogs)).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("reactive (api-gateway) filter: X-Internal-Auth / Authorization / Cookie are never logged")
    void reactiveFilterRedactsSecrets() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/orders")
                        .header("X-Internal-Auth", SECRET)
                        .header("Authorization", BEARER)
                        .header("Cookie", "session=abc123")
                        .header("X-User-Roles", "CUSTOMER"));
        WebFilterChain chain = ex -> Mono.empty();

        new ReactiveRequestTraceIdFilter().filter(exchange, chain).block();

        String logged = joined(reactiveLogs);
        assertThat(logged).contains("Incoming request POST /api/v1/orders")
                .contains("X-User-Roles=CUSTOMER");
        assertThat(logged).doesNotContain(SECRET).doesNotContain("X-Internal-Auth")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9").doesNotContain("abc123");
    }
}
