package com.farm2home.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID     = "X-User-Id";
    private static final String HEADER_USER_MOBILE = "X-User-Mobile";
    private static final String HEADER_USER_ROLES  = "X-User-Roles";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String userId      = request.getHeader(HEADER_USER_ID);
        String mobile      = request.getHeader(HEADER_USER_MOBILE);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);

        if (userId != null && mobile != null && rolesHeader != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Set<String> roles = Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isBlank())
                        .collect(Collectors.toSet());

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();

                UserPrincipal principal = new UserPrincipal(UUID.fromString(userId), mobile, roles);
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                log.warn("Failed to build authentication from gateway headers: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
