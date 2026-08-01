package com.farm2home.payment.config;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID    = HeaderConstants.X_USER_ID;
    private static final String HEADER_USER_MOBILE = HeaderConstants.X_USER_MOBILE;
    private static final String HEADER_USER_ROLES  = HeaderConstants.X_USER_ROLES;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        String mobile = request.getHeader(HEADER_USER_MOBILE);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);

        if (StringUtils.hasText(userId) && StringUtils.hasText(mobile)) {
            try {
                Set<String> roles = StringUtils.hasText(rolesHeader)
                        ? Arrays.stream(rolesHeader.split(","))
                                .map(String::trim).collect(Collectors.toSet())
                        : Set.of();

                UserPrincipal principal = new UserPrincipal(UUID.fromString(userId), mobile, roles);
                var authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + r))
                        .collect(Collectors.toList());

                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                log.warn("Failed to parse gateway headers: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
