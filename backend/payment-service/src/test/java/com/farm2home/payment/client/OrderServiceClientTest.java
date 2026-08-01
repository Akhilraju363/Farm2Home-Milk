package com.farm2home.payment.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServiceClient is a thin WebClient wrapper around order-service's GET /{id} endpoint -
 * rather than a real HTTP server, the WebClient.Builder is given a stub ExchangeFunction
 * returning a canned ApiResponse body, exercising the real URI-building/header-forwarding/
 * response-mapping code without a network dependency (same technique as dashboard-service's
 * and reports-service's client tests).
 */
class OrderServiceClientTest {

    private final RequestHeaderForwarder headerForwarder = new RequestHeaderForwarder();

    private WebClient.Builder stubbedBuilder(String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build()));
    }

    @Test
    @DisplayName("getOrder() parses the order status response")
    void getOrder_parsesResponse() {
        UUID orderId = UUID.randomUUID();
        OrderServiceClient client = new OrderServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"id\":\"" + orderId + "\",\"status\":\"PENDING\"}}"), headerForwarder);

        OrderStatusResponse result = client.getOrder(orderId).block();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getOrder() parses a CANCELLED order")
    void getOrder_cancelled() {
        UUID orderId = UUID.randomUUID();
        OrderServiceClient client = new OrderServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"id\":\"" + orderId + "\",\"status\":\"CANCELLED\"}}"), headerForwarder);

        OrderStatusResponse result = client.getOrder(orderId).block();

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }
}
