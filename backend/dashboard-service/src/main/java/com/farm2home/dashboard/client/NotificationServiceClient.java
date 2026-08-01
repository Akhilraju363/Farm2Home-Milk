package com.farm2home.dashboard.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;

import com.farm2home.common.core.dashboard.NotificationSummaryItem;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class NotificationServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public NotificationServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://notification-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<List<NotificationSummaryItem>> getRecent(int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications/summary")
                        .queryParam("limit", limit)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<NotificationSummaryItem>>>() { })
                .map(ApiResponse::getData);
    }
}
