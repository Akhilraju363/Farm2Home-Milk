package com.farm2home.customer.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Fetches Farm2Home's own delivery-origin location + radius from farm-service - the single
 *  source of truth (see farm-service's BusinessSettings) - for delivery-eligibility checks.
 *  Mirrors order-service's ProductionServiceClient/InventoryServiceClient exact shape. */
@Component
public class FarmServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public FarmServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://farm-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<BusinessSettingsDto> getBusinessSettings() {
        return webClient.get()
                .uri("/api/v1/farm/business-settings")
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessSettingsDto>>() { })
                .map(ApiResponse::getData);
    }
}
