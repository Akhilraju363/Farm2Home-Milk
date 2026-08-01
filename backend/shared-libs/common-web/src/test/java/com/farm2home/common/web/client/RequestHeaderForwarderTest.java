package com.farm2home.common.web.client;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.observability.web.RequestTraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHeaderForwarderTest {

    private final RequestHeaderForwarder forwarder = new RequestHeaderForwarder();

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    @DisplayName("no bound request, no MDC → empty headers")
    void noContext_returnsEmptyHeaders() {
        assertThat(forwarder.forwardable().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("bound request with identity headers → copies them onto the outgoing request")
    void boundRequest_copiesIdentityHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.X_USER_ID, "11111111-1111-1111-1111-111111111111");
        request.addHeader(HeaderConstants.X_USER_MOBILE, "9876543210");
        request.addHeader(HeaderConstants.X_USER_ROLES, "SUPER_ADMIN");
        bindRequest(request);

        var headers = forwarder.forwardable();

        assertThat(headers.getFirst(HeaderConstants.X_USER_ID)).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(headers.getFirst(HeaderConstants.X_USER_MOBILE)).isEqualTo("9876543210");
        assertThat(headers.getFirst(HeaderConstants.X_USER_ROLES)).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("bound request missing identity headers → those headers are simply absent")
    void boundRequest_missingHeaders_omitted() {
        bindRequest(new MockHttpServletRequest());

        var headers = forwarder.forwardable();

        assertThat(headers.containsKey(HeaderConstants.X_USER_ID)).isFalse();
    }

    @Test
    @DisplayName("correlation id present in MDC → forwarded as X-Correlation-ID")
    void mdcCorrelationId_forwarded() {
        MDC.put(RequestTraceIdFilter.MDC_CORRELATION_ID, "corr-123");

        var headers = forwarder.forwardable();

        assertThat(headers.getFirst(RequestTraceIdFilter.CORRELATION_ID_HEADER)).isEqualTo("corr-123");
    }

    private void bindRequest(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
