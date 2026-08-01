package com.farm2home.notification.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Kept off NotificationServiceApplication itself so @WebMvcTest slices (which include the
// @SpringBootApplication class as their config source) don't try to bootstrap JPA
// infrastructure that a controller-only slice has no EntityManagerFactory for.
//
// Both explicit for the same reason as every other service's JpaConfig: common-core's shared
// AuditLog/AuditLogRepository live outside this service's own package tree. common-core's own
// @EntityScan (scoped to its audit package) unions correctly with this one, but
// @EnableJpaRepositories does not compose with Spring Boot's implicit default scanning once ANY
// explicit one exists anywhere in the context - so this service's own package has to be listed
// explicitly here too, for both. Without this class, NotificationLog silently drops out of
// Hibernate's managed types the moment AuditAutoConfiguration's @EntityScan is the only one
// Spring finds, and notificationLogRepository fails to start with "Not a managed type".
//
// No @EnableJpaAuditing here (unlike order/payment/etc.'s JpaConfig): NotificationLog has no
// @CreatedBy/@LastModifiedBy fields and this service has no AuditorAwareImpl bean to reference.
@Configuration
@EntityScan(basePackages = "com.farm2home.notification")
@EnableJpaRepositories(basePackages = {"com.farm2home.notification", "com.farm2home.common.core.audit"})
public class JpaConfig {
}
