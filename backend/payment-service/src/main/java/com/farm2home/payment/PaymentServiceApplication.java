package com.farm2home.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
// Both explicit for the same reason: common-core's shared AuditLog/AuditLogRepository live
// outside this service's own package tree. common-core's own @EntityScan (scoped to its audit
// package) unions correctly with this one, but @EnableJpaRepositories does not compose with
// Spring Boot's implicit default scanning once ANY explicit one exists anywhere in the
// context - so this service's own package has to be listed explicitly here too, for both.
@EntityScan(basePackages = "com.farm2home.payment")
@EnableJpaRepositories(basePackages = {"com.farm2home.payment", "com.farm2home.common.core.audit"})
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
