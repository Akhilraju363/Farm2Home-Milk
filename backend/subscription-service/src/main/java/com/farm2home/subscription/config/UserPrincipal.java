package com.farm2home.subscription.config;

import java.util.Set;
import java.util.UUID;

/**
 * Populated by GatewayHeaderAuthFilter from headers forwarded by api-gateway.
 */
public record UserPrincipal(UUID userId, String mobile, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains("FARM_MANAGER") || roles.contains("SUPER_ADMIN");
    }
}
