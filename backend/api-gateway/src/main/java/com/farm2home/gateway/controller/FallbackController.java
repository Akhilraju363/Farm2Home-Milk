package com.farm2home.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Target of every route's CircuitBreaker fallbackUri (forward:/fallback/{service}).
 * One handler for all services rather than a per-service method, since the response
 * shape is identical - only the service name in the body differs.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping("/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable String service) {
        log.warn("Circuit breaker fallback triggered for '{}'", service);
        Map<String, Object> body = Map.of(
                "success", false,
                "service", service,
                "message", service + " is temporarily unavailable. Please try again shortly.",
                "timestamp", OffsetDateTime.now().toString()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
