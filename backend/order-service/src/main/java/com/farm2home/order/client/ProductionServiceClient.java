package com.farm2home.order.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Component
public class ProductionServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public ProductionServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://production-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<List<DailyProductionResponse>> getDailySummary(LocalDate from, LocalDate to) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/productions/summary")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DailyProductionResponse>>>() { })
                .map(ApiResponse::getData);
    }
}
