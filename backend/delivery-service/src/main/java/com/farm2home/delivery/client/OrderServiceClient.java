package com.farm2home.delivery.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class OrderServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public OrderServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://order-service").build();
        this.headerForwarder = headerForwarder;
    }

    /** Forwards the caller's identity - safe here because manualAssign() is FARM_MANAGER/
     *  SUPER_ADMIN only, and order-service's GET /orders/{id} treats both as admin (unlike
     *  customer-service's narrower GET /{id}, which excludes FARM_MANAGER - see the equivalent
     *  client in invoice-service, which had to use a system identity for exactly that reason). */
    public Mono<OrderDetailResponse> getOrder(UUID orderId) {
        return webClient.get()
                .uri("/api/v1/orders/{id}", orderId)
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() { })
                .map(ApiResponse::getData)
                .timeout(Duration.ofSeconds(5));
    }
}
