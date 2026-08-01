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
}
