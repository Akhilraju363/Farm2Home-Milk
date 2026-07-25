package com.farm2home.common.core.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaVendorAdapter;

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
 */
@AutoConfiguration
@ConditionalOnClass(JpaVendorAdapter.class)
@EntityScan(basePackages = "com.farm2home.common.core.audit")
public class AuditAutoConfiguration {

    @Bean
    public AuditLogService auditLogService(AuditLogRepository auditLogRepository) {
        return new AuditLogService(auditLogRepository);
    }
}
