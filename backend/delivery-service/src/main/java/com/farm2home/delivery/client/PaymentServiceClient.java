package com.farm2home.delivery.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class PaymentServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public PaymentServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://payment-service").build();
        this.headerForwarder = headerForwarder;
    }

    /** true if the order has a SUCCESS or PENDING payment. Not customer-scoped on the
     *  payment-service side, so this works correctly regardless of which role's identity is
     *  forwarded (delivery partner, admin, ...). */
    public Mono<Boolean> hasPayableProgress(UUID orderId) {
        return webClient.get()
                .uri("/api/v1/payments/order/{orderId}/payment-exists", orderId)
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Boolean>>() { })
                .map(ApiResponse::getData);
    }
}
