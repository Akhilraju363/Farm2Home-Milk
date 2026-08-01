package com.farm2home.dashboard.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;

import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SubscriptionServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public SubscriptionServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://subscription-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<SubscriptionSummaryResponse> getSummary() {
        return webClient.get()
                .uri("/api/v1/subscriptions/summary")
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<SubscriptionSummaryResponse>>() { })
                .map(ApiResponse::getData);
    }
}
