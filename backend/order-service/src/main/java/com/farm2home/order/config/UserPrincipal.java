package com.farm2home.order.config;

import java.util.Set;
import java.util.UUID;

public record UserPrincipal(UUID userId, String mobile, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains("FARM_MANAGER")
                || roles.contains("DELIVERY_MANAGER")
                || roles.contains("SUPER_ADMIN");
    }

    public boolean isDeliveryStaff() {
        return roles.contains("DELIVERY_MANAGER") || roles.contains("DELIVERY_PARTNER");
    }
}
