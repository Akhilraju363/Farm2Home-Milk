package com.farm2home.invoice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Kept off InvoiceServiceApplication itself so @WebMvcTest slices (which include the
// @SpringBootApplication class as their config source) don't try to bootstrap JPA
// infrastructure that a controller-only slice has no EntityManagerFactory for.
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EntityScan(basePackages = "com.farm2home.invoice")
@EnableJpaRepositories(basePackages = {"com.farm2home.invoice", "com.farm2home.common.core.audit"})
public class JpaConfig {
}
