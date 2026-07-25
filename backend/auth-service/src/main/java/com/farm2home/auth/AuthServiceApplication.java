package com.farm2home.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableScheduling
// Both explicit for the same reason: common-core's shared AuditLog/AuditLogRepository live
// outside this service's own package tree. common-core's own @EntityScan (scoped to its audit
// package) unions correctly with this one, but @EnableJpaRepositories does not compose with
// Spring Boot's implicit default scanning once ANY explicit one exists anywhere in the
// context - so this service's own package has to be listed explicitly here too, for both.
@EntityScan(basePackages = "com.farm2home.auth")
@EnableJpaRepositories(basePackages = {"com.farm2home.auth", "com.farm2home.common.core.audit"})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
