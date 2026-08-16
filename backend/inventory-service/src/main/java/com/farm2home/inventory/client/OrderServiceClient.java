package com.farm2home.inventory.client;

import com.farm2home.common.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class OrderServiceClient {

    private final WebClient webClient;

    public OrderServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://order-service").build();
    }

    public Mono<OrderDetailResponse> getOrder(UUID orderId) {
        return webClient.get()
                .uri("/api/v1/orders/{id}", orderId)
                .headers(SystemIdentityHeaders::apply)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() { })
                .map(ApiResponse::getData)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(ex -> {
                    log.warn("Could not resolve order {}: {}", orderId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
