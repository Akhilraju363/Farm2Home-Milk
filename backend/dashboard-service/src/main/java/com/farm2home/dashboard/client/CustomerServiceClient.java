package com.farm2home.dashboard.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;

import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class CustomerServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public CustomerServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://customer-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<CustomerSummaryResponse> getSummary() {
        return webClient.get()
                .uri("/api/v1/customers/summary")
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<CustomerSummaryResponse>>() { })
                .map(ApiResponse::getData);
    }
}
