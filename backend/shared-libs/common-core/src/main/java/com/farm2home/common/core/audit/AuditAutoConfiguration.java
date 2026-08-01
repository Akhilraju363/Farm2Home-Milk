package com.farm2home.common.core.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @EntityScan placed here still takes effect in every consuming service's application context
 * even though this class isn't in that service's own package tree - Spring Boot's
 * EntityScanPackages mechanism unions packages from every @EntityScan it finds, including ones
 * contributed by auto-configuration classes from a dependency jar.
 *
 * Deliberately NOT using @EnableJpaRepositories here: unlike @EntityScan, it does not compose -
 * its mere presence anywhere in the context makes Spring Boot back off from its own implicit
 * repository scanning entirely, which would silently break every consuming service's own
 * repositories (confirmed the hard way: auth-service's UserRepository stopped resolving).
 * Each consuming service must declare its own @EnableJpaRepositories covering both its own
 * repository package and com.farm2home.common.core.audit.
 *
 * @EnableAsync here (rather than requiring every service to declare it) makes AuditLogService's
 * writes fire-and-forget platform-wide with zero per-service wiring; it's harmless to enable
 * async proxying even in services that add no other @Async beans of their own.
 */
@AutoConfiguration
@ConditionalOnClass(JpaVendorAdapter.class)
@EntityScan(basePackages = "com.farm2home.common.core.audit")
@EnableAsync
public class AuditAutoConfiguration {

    @Bean
    public AuditLogService auditLogService(AuditLogRepository auditLogRepository,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        return new AuditLogService(auditLogRepository, serviceName);
    }

    @Bean
    AuditAspect auditAspect(AuditLogService auditLogService) {
        return new AuditAspect(auditLogService);
    }

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-log-");
        executor.initialize();
        return executor;
    }
}
