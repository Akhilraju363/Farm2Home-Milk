package com.farm2home.app.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The single application-level JPA configuration for the spike.
 *
 * <p>Replaces both {@code com.farm2home.farm.config.JpaConfig} and
 * {@code com.farm2home.production.config.JpaConfig} (neither is component-scanned). Those two
 * each carry their own {@code @EnableJpaRepositories}, and — as their own in-code comments
 * document — {@code @EnableJpaRepositories} does <b>not</b> compose: once more than one exists
 * in a context they stop unioning cleanly and Spring Boot's implicit repository scanning backs
 * off. So there is exactly one here, listing every repository package that must be discovered:
 *
 * <ul>
 *   <li>{@code com.farm2home.farm.domain.repository} — FarmRepository, CowRepository,
 *       VaccinationRepository, HealthRecordRepository, BusinessSettingsRepository;</li>
 *   <li>{@code com.farm2home.production.domain.repository} — MilkProductionRepository;</li>
 *   <li>{@code com.farm2home.common.core.audit} — the shared AuditLogRepository, discovered
 *       exactly once.</li>
 * </ul>
 *
 * <p>{@code @EntityScan} lists the matching entity packages. common-core's
 * {@code AuditAutoConfiguration} also contributes an {@code @EntityScan} for its audit package;
 * Spring Boot unions {@code @EntityScan} packages (unlike {@code @EnableJpaRepositories}), so
 * listing it here too is redundant-but-safe and keeps this file self-describing.
 *
 * <p>{@code @EnableJpaAuditing} wires the {@code createdBy}/{@code updatedBy} population to the
 * {@code auditorAwareImpl} bean supplied by {@link SpikeAuditorAware} (same bean name both
 * services' excluded configs used).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EntityScan(basePackages = {
        // Group 1
        "com.farm2home.farm.domain.entity",
        "com.farm2home.production.domain.entity",
        // Group 2
        "com.farm2home.customer.domain.entity",
        "com.farm2home.inventory.domain.entity",
        // Group 3
        "com.farm2home.subscription.domain.entity",
        "com.farm2home.order.domain.entity",
        // Group 4
        "com.farm2home.payment.domain.entity",
        "com.farm2home.delivery.domain.entity",
        // Group 5
        "com.farm2home.notification.domain.entity",
        "com.farm2home.invoice.domain.entity",
        // shared
        "com.farm2home.common.core.audit"
})
@EnableJpaRepositories(basePackages = {
        // Group 1
        "com.farm2home.farm.domain.repository",
        "com.farm2home.production.domain.repository",
        // Group 2
        "com.farm2home.customer.domain.repository",
        "com.farm2home.inventory.domain.repository",
        // Group 3
        "com.farm2home.subscription.domain.repository",
        "com.farm2home.order.domain.repository",
        // Group 4
        "com.farm2home.payment.domain.repository",
        "com.farm2home.delivery.domain.repository",
        // Group 5
        "com.farm2home.notification.domain.repository",
        "com.farm2home.invoice.domain.repository",
        // shared audit repository — discovered exactly once
        "com.farm2home.common.core.audit"
})
public class SpikeJpaConfig {
}
