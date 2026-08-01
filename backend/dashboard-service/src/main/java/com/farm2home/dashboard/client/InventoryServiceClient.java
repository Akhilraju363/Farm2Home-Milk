package com.farm2home.dashboard.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;

import com.farm2home.common.core.dashboard.InventorySummaryResponse;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class InventoryServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public InventoryServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://inventory-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<InventorySummaryResponse> getSummary(int topItemsLimit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/inventory/summary")
                        .queryParam("limit", topItemsLimit)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<InventorySummaryResponse>>() { })
                .map(ApiResponse::getData);
    }
}
