package com.farm2home.common.core.constants;

/** Role names, JWT claim keys, and token conventions shared across services.
 *  Role names here must match the seed data in auth-service's
 *  V1__init_auth_schema.sql - they are not independently re-derived. */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    // ── Roles ───────────────────────────────────────────────────────────────────
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_FARM_MANAGER = "FARM_MANAGER";
    public static final String ROLE_DELIVERY_MANAGER = "DELIVERY_MANAGER";
    public static final String ROLE_DELIVERY_PARTNER = "DELIVERY_PARTNER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    /** Prepended by some (not all) services' GatewayHeaderAuthFilter before building
     *  authorities, to pair with @PreAuthorize's hasAnyRole(...) in that same service.
     *  Services using hasAnyAuthority(...) instead must NOT apply this prefix - see
     *  each service's GatewayHeaderAuthFilter for which convention it follows. */
    public static final String ROLE_PREFIX = "ROLE_";

    // ── JWT ─────────────────────────────────────────────────────────────────────
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";
}
