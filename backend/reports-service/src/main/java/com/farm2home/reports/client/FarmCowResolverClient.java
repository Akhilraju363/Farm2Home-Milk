package com.farm2home.reports.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the Production Report's "Farm" filter: production-service only knows cowId (it has
 * no farm linkage of its own), so a farm filter is resolved to the farm's cow ids here first via
 * farm-service's dedicated lookup endpoint, then passed to ProductionReportClient as a cowIds
 * filter. Reuses farm-service's own endpoint rather than duplicating cow/farm query logic.
 */
@Component
public class FarmCowResolverClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public FarmCowResolverClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://farm-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<List<UUID>> getCowIdsForFarm(UUID farmId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/farm/cows/ids")
                        .queryParam("farmId", farmId)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<UUID>>>() { })
                .map(ApiResponse::getData);
    }
}
