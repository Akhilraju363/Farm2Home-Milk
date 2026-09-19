package com.farm2home.app.config;

import com.farm2home.app.security.SpikePrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Supplies the {@code auditorAwareImpl} bean that {@link SpikeJpaConfig}'s
 * {@code @EnableJpaAuditing} references — replacing the two excluded
 * {@code com.farm2home.{farm,production}.config.AuditorAwareImpl} components (which are
 * {@code instanceof}-checked against their own package-private {@code UserPrincipal} record and
 * would both register under the same bean name).
 *
 * <p>Same resolution rule the originals used: the authenticated principal's mobile number, or
 * {@code "system"} when there is no authentication (e.g. Flyway-time / async audit writes).
 */
@Configuration
public class SpikeAuditorAware {

    @Bean(name = "auditorAwareImpl")
    public AuditorAware<String> auditorAwareImpl() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("system");
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof SpikePrincipal sp && sp.mobile() != null) {
                return Optional.of(sp.mobile());
            }
            return Optional.of(auth.getName());
        };
    }
}
