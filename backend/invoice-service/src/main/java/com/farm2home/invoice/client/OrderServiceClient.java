package com.farm2home.invoice.client;

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
                // Without this, a slow/unreachable order-service leaves a blocking caller (invoice
                // generation, PDF rendering) waiting indefinitely - see the class-level pattern
                // already established in payment-service's OrderServiceClient.
                .timeout(Duration.ofSeconds(5));
    }
}
