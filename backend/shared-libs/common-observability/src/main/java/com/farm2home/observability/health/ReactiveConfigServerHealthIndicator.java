package com.farm2home.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * WebFlux equivalent of {@link ConfigServerHealthIndicator} for the API Gateway - same
 * rationale (no service actually imports config from Config Server, so nothing auto-registers
 * this check). Uses whatever reactive HTTP connector is already on the classpath (reactor-netty,
 * pulled in transitively via spring-cloud-starter-gateway) rather than common-observability
 * declaring its own, and a Mono-level timeout instead of connector-specific configuration so no
 * extra dependency is needed here.
 */
public class ReactiveConfigServerHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;
    private final String healthUrl;

    public ReactiveConfigServerHealthIndicator(String baseUrl, String username, String password) {
        this.healthUrl = baseUrl + "/actuator/health";
        this.webClient = WebClient.builder()
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }

    @Override
    public Mono<Health> health() {
        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(body -> Health.up()
                        .withDetail("url", healthUrl)
                        .withDetail("response", body)
                        .build())
                .onErrorResume(ex -> Mono.just(Health.down()
                        .withDetail("url", healthUrl)
                        .withException(ex)
                        .build()));
    }
}
