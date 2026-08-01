package com.farm2home.reports.config;

import com.farm2home.common.core.constants.SecurityConstants;

import java.util.Set;
import java.util.UUID;

public record UserPrincipal(UUID userId, String mobile, Set<String> roles) {
    public boolean isAdmin() {
        return roles.contains(SecurityConstants.ROLE_SUPER_ADMIN);
    }
}
