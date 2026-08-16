package com.farm2home.inventory.client;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import org.springframework.http.HttpHeaders;

/** Shared by every cross-service client in this package - mirrors invoice-service's
 *  SystemIdentityHeaders. The caller who triggered the request (a CUSTOMER submitting a review)
 *  isn't necessarily authorized to read the underlying order under order-service's OWN ownership
 *  rules the same way, so these calls present a fixed system identity with SUPER_ADMIN standing;
 *  ReviewServiceImpl re-derives the real ownership/eligibility check itself from the response. */
final class SystemIdentityHeaders {

    private static final String SYSTEM_CALLER_ID = "00000000-0000-0000-0000-000000000000";
    private static final String SYSTEM_CALLER_MOBILE = "inventory-service";

    private SystemIdentityHeaders() {
    }

    static void apply(HttpHeaders headers) {
        headers.add(HeaderConstants.X_USER_ID, SYSTEM_CALLER_ID);
        headers.add(HeaderConstants.X_USER_MOBILE, SYSTEM_CALLER_MOBILE);
        headers.add(HeaderConstants.X_USER_ROLES, SecurityConstants.ROLE_SUPER_ADMIN);
    }
}
