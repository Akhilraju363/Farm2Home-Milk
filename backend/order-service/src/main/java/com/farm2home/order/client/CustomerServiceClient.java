package com.farm2home.order.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Revalidates delivery-radius eligibility at order-creation time (see
 *  OrderServiceImpl.createManualOrder) by reusing customer-service's own ownership-scoped
 *  endpoint, rather than re-implementing the Haversine distance check here - see
 *  GeoDistanceUtil/DeliveryAvailabilityServiceImpl in customer-service for the actual
 *  calculation. Mirrors ProductionServiceClient/InventoryServiceClient's exact shape. Uses the
 *  {id}-scoped endpoint (not /me/) so this also works correctly when an admin creates the order
 *  on behalf of a different customer - header-forwarding carries the ADMIN's own identity, and
 *  customer-service's ownership check on that endpoint allows an admin caller through for any id. */
@Component
public class CustomerServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public CustomerServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://customer-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<DeliveryAvailabilityResponse> getDeliveryAvailability(UUID customerId) {
        return webClient.get()
                .uri("/api/v1/customers/{id}/delivery-availability", customerId)
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<DeliveryAvailabilityResponse>>() { })
                .map(ApiResponse::getData);
    }
}
