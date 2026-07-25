package com.farm2home.delivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Kept off DeliveryServiceApplication itself so @WebMvcTest slices (which include the
// @SpringBootApplication class as their config source) don't try to bootstrap JPA
// infrastructure that a controller-only slice has no EntityManagerFactory for.
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaConfig {
}
