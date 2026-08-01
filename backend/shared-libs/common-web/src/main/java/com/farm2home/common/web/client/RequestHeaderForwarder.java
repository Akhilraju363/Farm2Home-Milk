package com.farm2home.common.web.client;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.observability.web.RequestTraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Shared by every BFF-style aggregator/proxy service (dashboard-service, reports-service, ...):
 * their downstream calls hit each owning service's normal REST endpoints, which are secured
 * exactly like every other endpoint (GatewayHeaderAuthFilter + @PreAuthorize), so the identity
 * headers the gateway would have attached must be re-derived from the inbound request the
 * aggregator itself received and forwarded, or the downstream call is rejected as unauthenticated.
 *
 * No @Component here - this lives outside any consuming service's own component-scan base
 * package, so it's registered explicitly as a bean by CommonWebAutoConfiguration instead
 * (same reasoning as AuditLogService in common-core).
 */
public class RequestHeaderForwarder {

    public HttpHeaders forwardable() {
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest request = currentRequest();
        if (request != null) {
            copyIfPresent(request, headers, HeaderConstants.X_USER_ID);
            copyIfPresent(request, headers, HeaderConstants.X_USER_MOBILE);
            copyIfPresent(request, headers, HeaderConstants.X_USER_ROLES);
        }
        String correlationId = MDC.get(RequestTraceIdFilter.MDC_CORRELATION_ID);
        if (StringUtils.hasText(correlationId)) {
            headers.add(RequestTraceIdFilter.CORRELATION_ID_HEADER, correlationId);
        }
        return headers;
    }

    private void copyIfPresent(HttpServletRequest request, HttpHeaders headers, String name) {
        String value = request.getHeader(name);
        if (StringUtils.hasText(value)) {
            headers.add(name, value);
        }
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest() : null;
    }
}
