package com.farm2home.common.web.client;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.web.security.GatewayTrust;
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

    private final GatewayTrust gatewayTrust;

    public RequestHeaderForwarder(GatewayTrust gatewayTrust) {
        this.gatewayTrust = gatewayTrust;
    }

    /** Convenience for tests / non-Spring use: no gateway-trust enforcement, so identity headers
     *  are forwarded without an {@code X-Internal-Auth} - same behaviour as before this field
     *  existed. Production wiring uses {@link #RequestHeaderForwarder(GatewayTrust)}. */
    public RequestHeaderForwarder() {
        this(new GatewayTrust(""));
    }

    public HttpHeaders forwardable() {
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest request = currentRequest();
        if (request != null) {
            copyIfPresent(request, headers, HeaderConstants.X_USER_ID);
            copyIfPresent(request, headers, HeaderConstants.X_USER_MOBILE);
            copyIfPresent(request, headers, HeaderConstants.X_USER_ROLES);
            // Also forward the caller's bearer token. In the microservice deployment this is a
            // harmless no-op (downstream services authenticate from the X-User-* headers above,
            // and the gateway already passes Authorization through to every routed service). In
            // the single-JVM aggregation it is what actually authenticates a loopback
            // lb://<service> call: that call re-enters this same process, whose servlet JWT
            // filter validates the forwarded token and re-establishes the identity - no
            // header-trust needed. Only forwarded for internal (lb://) clients, which is all
            // this class is used by.
            copyIfPresent(request, headers, HeaderConstants.AUTHORIZATION);
        }
        // Re-attach the gateway trust secret so the downstream service accepts the forwarded
        // identity. This hop is itself trusted: it only runs inside a request that already
        // passed this service's own GatewayTrust check. No-op when enforcement is disabled.
        if (gatewayTrust != null && gatewayTrust.isEnforcing()
                && headers.containsKey(HeaderConstants.X_USER_ID)) {
            headers.add(HeaderConstants.X_INTERNAL_AUTH, gatewayTrust.secret());
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
