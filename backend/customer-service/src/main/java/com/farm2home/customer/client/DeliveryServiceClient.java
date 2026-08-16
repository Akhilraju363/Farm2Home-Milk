package com.farm2home.customer.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Read-only lookup of delivery-service's active routes, for
 *  DeliveryRouteSelectionServiceImpl's automatic route selection. This is a one-directional
 *  dependency only (customer-service -> delivery-service) - delivery-service has no client back
 *  into customer-service, so this does not create a circular HTTP dependency. Route-selection
 *  logic itself lives here, not in delivery-service, specifically to avoid that cycle: delivery-
 *  service already calls order-service (OrderServiceClient, for manualAssign's order lookup), so
 *  order-service calling delivery-service directly would close a loop; routing selection through
 *  customer-service (which order-service already calls for delivery-eligibility) avoids it. */
@Component
public class DeliveryServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public DeliveryServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://delivery-service").build();
        this.headerForwarder = headerForwarder;
    }

    /** GET /delivery/routes/search?active=true - open to any authenticated caller on
     *  delivery-service's side (DeliveryRouteController.search has no @PreAuthorize), so this
     *  works regardless of which caller (customer or admin) ultimately triggered the delivery-
     *  eligibility check that needs it. size=200 comfortably covers the handful of routes this
     *  application actually has - no pagination loop needed. */
    public Mono<RoutePageDto> getActiveRoutes() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/delivery/routes/search")
                        .queryParam("active", true)
                        .queryParam("size", 200)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<RoutePageDto>>() { })
                .map(ApiResponse::getData);
    }
}
