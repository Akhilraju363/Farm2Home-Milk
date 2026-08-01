package com.farm2home.common.core.audit;

import com.farm2home.common.core.constants.HeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the identity/HTTP fields every audit row wants (userId, username, ip, uri, method) off
 * the current request, when there is one. userId/username come from the gateway-injected headers
 * (see HeaderConstants) rather than SecurityContext, since every service's GatewayHeaderAuthFilter
 * already derives its principal from those same headers - reading them directly here works
 * uniformly across services without needing to know each service's own principal type.
 *
 * Must only be called from the thread handling the request: request/thread binding is not
 * propagated to @Async threads, so AuditLogService captures this synchronously before dispatching.
 */
final class AuditHttpContext {

    private AuditHttpContext() {
    }

    static HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    static String userId(HttpServletRequest request) {
        return request != null ? request.getHeader(HeaderConstants.X_USER_ID) : null;
    }

    static String username(HttpServletRequest request) {
        return request != null ? request.getHeader(HeaderConstants.X_USER_MOBILE) : null;
    }

    static String ipAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
