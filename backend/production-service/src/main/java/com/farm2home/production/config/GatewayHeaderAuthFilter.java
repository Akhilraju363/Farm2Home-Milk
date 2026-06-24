package com.farm2home.production.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId  = request.getHeader("X-User-Id");
        String mobile  = request.getHeader("X-User-Mobile");
        String rolesHdr = request.getHeader("X-User-Roles");

        if (StringUtils.hasText(userId) && StringUtils.hasText(mobile)) {
            Set<String> roles = StringUtils.hasText(rolesHdr)
                    ? Arrays.stream(rolesHdr.split(",")).map(String::trim).collect(Collectors.toSet())
                    : Set.of();

            UserPrincipal principal = new UserPrincipal(UUID.fromString(userId), mobile, roles);
            var authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
