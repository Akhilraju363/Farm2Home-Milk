package com.farm2home.delivery.config;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String mobile = request.getHeader("X-User-Mobile");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (StringUtils.hasText(userId) && StringUtils.hasText(mobile)) {
            try {
                Set<String> roles = StringUtils.hasText(rolesHeader)
                        ? Arrays.stream(rolesHeader.split(",")).map(String::trim).collect(Collectors.toSet())
                        : Set.of();
                var principal = new UserPrincipal(UUID.fromString(userId), mobile, roles);
                var authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (Exception e) {
                log.warn("Failed to parse gateway headers: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
