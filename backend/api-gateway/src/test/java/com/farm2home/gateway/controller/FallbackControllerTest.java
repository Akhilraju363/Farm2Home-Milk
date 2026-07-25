package com.farm2home.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    @DisplayName("returns 503 with the service name in the body")
    void fallback_returns503WithServiceName() {
        ResponseEntity<Map<String, Object>> response = controller.fallback("auth-service").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("service", "auth-service");
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody().get("message")).asString().contains("auth-service");
    }
}
