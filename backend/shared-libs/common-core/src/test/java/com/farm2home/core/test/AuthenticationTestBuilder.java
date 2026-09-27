package com.farm2home.core.test;

import com.farm2home.common.core.constants.HeaderConstants;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

/**
 * Utility class for building authenticated requests in integration tests.
 *
 * <p>Authenticates the same way production traffic does: by sending the identity headers the
 * api-gateway attaches after validating the JWT ({@code X-User-Id}, {@code X-User-Mobile},
 * {@code X-User-Roles}). Each service's own {@code GatewayHeaderAuthFilter} then builds that
 * service's own {@code UserPrincipal} from them, so {@code @AuthenticationPrincipal} and
 * {@code @PreAuthorize} resolve exactly as they would behind the gateway. This keeps the helper
 * service-agnostic - it never needs to know any service's principal type.
 *
 * <p>Roles are bare role names (e.g. {@code CUSTOMER}, {@code FARM_MANAGER}), matching the
 * authorities the filters grant and the {@code hasAnyAuthority(...)} checks controllers use - no
 * {@code ROLE_} prefix.
 */
public class AuthenticationTestBuilder {

    private UUID userId;
    private String mobile;
    private Set<String> roles;

    public AuthenticationTestBuilder() {
        this.userId = UUID.randomUUID();
        this.mobile = "9876543210";
        this.roles = Set.of("CUSTOMER");
    }

    public AuthenticationTestBuilder withUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public AuthenticationTestBuilder withMobile(String mobile) {
        this.mobile = mobile;
        return this;
    }

    /**
     * @deprecated the gateway identifies callers by mobile, not username - use {@link #withMobile}.
     * Kept as an alias so existing callers still compile; the value is sent as {@code X-User-Mobile}.
     */
    @Deprecated
    public AuthenticationTestBuilder withUsername(String username) {
        return withMobile(username);
    }

    public AuthenticationTestBuilder withRoles(String... roles) {
        this.roles = Set.of(roles);
        return this;
    }

    public RequestPostProcessor build() {
        String userIdHeader = userId.toString();
        String mobileHeader = mobile;
        String rolesHeader = String.join(",", roles);
        return request -> {
            request.addHeader(HeaderConstants.X_USER_ID, userIdHeader);
            request.addHeader(HeaderConstants.X_USER_MOBILE, mobileHeader);
            request.addHeader(HeaderConstants.X_USER_ROLES, rolesHeader);
            return request;
        };
    }
}
