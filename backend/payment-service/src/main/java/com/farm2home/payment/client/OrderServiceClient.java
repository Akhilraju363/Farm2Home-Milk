package com.farm2home.payment.client;

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

    /** Forwards the caller's own identity headers, so order-service applies the exact same
     *  ownership scoping it would for a direct call (a customer paying for their own order
     *  resolves fine; an admin paying on a customer's behalf resolves fine via their admin
     *  authority; a customer trying to pay for someone else's order gets a 404 from
     *  order-service itself, exactly as if they'd called it directly). */
    public Mono<OrderStatusResponse> getOrder(UUID orderId) {
        return webClient.get()
                .uri("/api/v1/orders/{id}", orderId)
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderStatusResponse>>() { })
                .map(ApiResponse::getData)
                // Without this, a slow/unreachable order-service (or a stuck Eureka lb://
                // resolution) leaves initiate()'s .block() waiting indefinitely - see
                // PaymentServiceImpl.verifyOrderIsPayable, which maps this to a friendly error.
                .timeout(Duration.ofSeconds(5));
    }
}
