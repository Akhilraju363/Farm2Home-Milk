package com.farm2home.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * dashboard-service has no database of its own (pure aggregator over other services' APIs), so
 * unlike every other service's *ApplicationTests this doesn't need Testcontainers/Postgres -
 * it's a plain context-load smoke test confirming every bean (WebClient clients, security
 * filter chain, DashboardService/Controller) wires up correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardServiceApplicationTests {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> false);
        registry.add("eureka.client.register-with-eureka", () -> false);
        registry.add("eureka.client.fetch-registry", () -> false);
    }

    @Test
    void contextLoads() {
    }
}
