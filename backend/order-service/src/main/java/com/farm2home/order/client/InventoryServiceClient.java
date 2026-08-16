package com.farm2home.order.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Resolves a real inventory-service Product when a customer orders one directly (see
 *  OrderServiceImpl.createManualOrder) - mirrors ProductionServiceClient's exact shape, the
 *  existing precedent for a synchronous cross-service read from order-service. */
@Component
public class InventoryServiceClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public InventoryServiceClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://inventory-service").build();
        this.headerForwarder = headerForwarder;
    }

    /** Empty (not an error) if the product doesn't exist - the caller treats that the same as
     *  "product not found", not a transient failure. Any other failure (service down, etc.)
     *  propagates as a WebClientException, same as ProductionServiceClient. */
    public Mono<ProductDetailResponse> getProduct(UUID productId) {
        return webClient.get()
                .uri("/api/v1/inventory/products/{id}", productId)
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ProductDetailResponse>>() { })
                .map(ApiResponse::getData)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    /** Atomically reserves stock for a product-based order item - see inventory-service's
     *  ProductRepository.decrementStock for why this single call (not a separate "check then
     *  deduct" pair) is what actually makes concurrent checkouts of the same product race-safe.
     *  A 409 here (lost the race / stock changed since the fast-fail check in priceItem()) is
     *  intentionally NOT swallowed - it propagates as a WebClientResponseException.Conflict for
     *  the caller to turn into a clear OrderException. */
    public Mono<ProductDetailResponse> decrementStock(UUID productId, int quantity) {
        DecrementStockRequest request = new DecrementStockRequest();
        request.setQuantity(quantity);
        return webClient.post()
                .uri("/api/v1/inventory/products/{id}/decrement-stock", productId)
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ProductDetailResponse>>() { })
                .map(ApiResponse::getData);
    }
}
