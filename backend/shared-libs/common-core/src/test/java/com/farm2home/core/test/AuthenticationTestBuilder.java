package com.farm2home.core.test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Utility class for building authenticated requests in integration tests.
 */
public class AuthenticationTestBuilder {

    private UUID userId;
    private String username;
    private Set<String> roles;

    public AuthenticationTestBuilder() {
        this.userId = UUID.randomUUID();
        this.username = "testuser";
        this.roles = Set.of("CUSTOMER");
    }

    public AuthenticationTestBuilder withUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public AuthenticationTestBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public AuthenticationTestBuilder withRoles(String... roles) {
        this.roles = Set.of(roles);
        return this;
    }

    public RequestPostProcessor build() {
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        var principal = new UserPrincipal(userId, username);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );

        return authentication(auth);
    }

    /**
     * Simple user principal for testing. Replace with actual UserPrincipal from your auth-service.
     */
    public static class UserPrincipal {
        private final UUID userId;
        private final String username;

        public UserPrincipal(UUID userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public UUID getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }
    }
}
