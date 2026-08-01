package com.farm2home.common.core.constants;

/** API-wide conventions: the versioned base path, the actuator/docs endpoints every
 *  service exposes unauthenticated, and the default pagination size. */
public final class ApiConstants {

    private ApiConstants() {
    }

    public static final String API_V1 = "/api/v1";

    /** The subset of permitAll() paths shared by every service (actuator + API docs).
     *  Individual services still compose their own array on top of this - e.g. adding
     *  "/uploads/**" or business-specific public endpoints - since the full set is not
     *  identical across services (see each service's SecurityConfig). Do not mutate. */
    public static final String[] PUBLIC_ENDPOINTS_BASE = {
            "/actuator/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    public static final int DEFAULT_PAGE_SIZE = 20;
}
