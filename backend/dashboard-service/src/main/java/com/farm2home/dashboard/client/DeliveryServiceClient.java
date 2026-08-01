package com.farm2home.dashboard.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;

import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DeliveryServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public DeliveryServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://delivery-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<DeliverySummaryResponse> getSummary() {
        return webClient.get()
                .uri("/api/v1/delivery/assignments/summary")
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<DeliverySummaryResponse>>() { })
                .map(ApiResponse::getData);
    }
}
