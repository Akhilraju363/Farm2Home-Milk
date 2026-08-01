package com.farm2home.delivery.config;

import com.farm2home.common.core.constants.SecurityConstants;

import java.util.Set;
import java.util.UUID;

public record UserPrincipal(UUID userId, String mobile, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains(SecurityConstants.ROLE_FARM_MANAGER) || roles.contains(SecurityConstants.ROLE_SUPER_ADMIN);
    }

    public boolean isDeliveryPartner() {
        return roles.contains(SecurityConstants.ROLE_DELIVERY_PARTNER);
    }
}
