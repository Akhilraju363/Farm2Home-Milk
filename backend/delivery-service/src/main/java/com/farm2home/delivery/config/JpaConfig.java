package com.farm2home.delivery.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Kept off DeliveryServiceApplication itself so @WebMvcTest slices (which include the
// @SpringBootApplication class as their config source) don't try to bootstrap JPA
// infrastructure that a controller-only slice has no EntityManagerFactory for.
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
// Both explicit for the same reason: common-core's shared AuditLog/AuditLogRepository live
// outside this service's own package tree. common-core's own @EntityScan (scoped to its audit
// package) unions correctly with this one, but @EnableJpaRepositories does not compose with
// Spring Boot's implicit default scanning once ANY explicit one exists anywhere in the
// context - so this service's own package has to be listed explicitly here too, for both.
// Without these two, this service's own entities silently drop out of Hibernate's managed
// types the moment AuditAutoConfiguration's @EntityScan is the only one Spring finds.
@EntityScan(basePackages = "com.farm2home.delivery")
@EnableJpaRepositories(basePackages = {"com.farm2home.delivery", "com.farm2home.common.core.audit"})
public class JpaConfig {
}
