package com.farm2home.customer.config;

import com.farm2home.common.core.constants.SecurityConstants;

import java.util.Set;
import java.util.UUID;

public record UserPrincipal(UUID userId, String mobile, Set<String> roles) {
    // FARM_MANAGER added alongside SUPER_ADMIN/DELIVERY_MANAGER so a FARM_MANAGER managing
    // subscriptions (subscription-service treats FARM_MANAGER as admin there) can resolve a
    // customer's name/mobile via GET /customers/{id} - without this they could create/manage a
    // subscription "for" a customerId but never see whose it is. GET /customers (list) and
    // /export stay SUPER_ADMIN/DELIVERY_MANAGER only (see their own @PreAuthorize) - this only
    // widens the ownership-bypass used by the per-id endpoints and /search.
    public boolean isAdmin() {
        return roles.contains(SecurityConstants.ROLE_SUPER_ADMIN)
                || roles.contains(SecurityConstants.ROLE_DELIVERY_MANAGER)
                || roles.contains(SecurityConstants.ROLE_FARM_MANAGER);
    }
}
