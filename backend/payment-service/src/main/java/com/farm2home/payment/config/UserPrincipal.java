package com.farm2home.payment.config;

import java.util.Set;
import java.util.UUID;

public record UserPrincipal(UUID userId, String mobile, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains("FARM_MANAGER") || roles.contains("SUPER_ADMIN");
    }
}
