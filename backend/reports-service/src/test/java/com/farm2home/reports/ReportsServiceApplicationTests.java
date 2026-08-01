package com.farm2home.reports;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * reports-service has no database of its own (pure aggregator/proxy over other services' APIs),
 * so unlike every other service's *ApplicationTests this doesn't need Testcontainers/Postgres -
 * it's a plain context-load smoke test confirming every bean wires up correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportsServiceApplicationTests {

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
