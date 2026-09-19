package com.farm2home.common.core.constants;

/** HTTP header names used for gateway-to-service identity propagation.
 *  The correlation-ID header (X-Correlation-ID) is intentionally NOT duplicated here -
 *  it already lives in common-observability's RequestTraceIdFilter, a lower-level module
 *  that common-core depends on (not the other way around), so it stays there as the
 *  single source of truth rather than being pulled up into common-core. */
public final class HeaderConstants {

    private HeaderConstants() {
    }

    public static final String AUTHORIZATION = "Authorization";
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_MOBILE = "X-User-Mobile";
    public static final String X_USER_ROLES = "X-User-Roles";

    /** Proof-of-origin header. The api-gateway sets this to a shared secret on every request
     *  it forwards (and strips any client-supplied copy first); downstream services trust the
     *  {@code X-User-*} identity headers only when this header matches their configured secret.
     *  Never sent to or set by a browser. See common-web's {@code GatewayTrust}. */
    public static final String X_INTERNAL_AUTH = "X-Internal-Auth";

    /** All identity/trust headers the gateway owns end-to-end - a client must never be able to
     *  supply any of these. The gateway removes every one of them from the inbound request
     *  before (re)setting them itself. */
    public static final String[] GATEWAY_OWNED_HEADERS = {
            X_USER_ID, X_USER_MOBILE, X_USER_ROLES, X_INTERNAL_AUTH
    };
}
