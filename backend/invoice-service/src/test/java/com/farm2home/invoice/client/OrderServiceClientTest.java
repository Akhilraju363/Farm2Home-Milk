package com.farm2home.invoice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServiceClient is a thin WebClient wrapper - the WebClient.Builder is given a stub
 * ExchangeFunction returning a canned ApiResponse body, exercising the real URI-building/
 * header-forwarding/response-mapping code without a network dependency (same technique used
 * by the other cross-service client classes in this package).
 */
class OrderServiceClientTest {

    private final AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();

    private WebClient.Builder stubbedBuilder(String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .build());
        });
    }

    @Test
    @DisplayName("getOrder() parses the order and its line items, and sends the fixed system identity headers")
    void getOrder_parsesResponseAndSendsSystemHeaders() {
        UUID orderId = UUID.randomUUID();
        OrderServiceClient client = new OrderServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"id\":\"" + orderId + "\",\"orderNumber\":\"ORD-2026-000042\","
                        + "\"status\":\"DELIVERED\",\"totalAmount\":120.00,"
                        + "\"items\":[{\"milkType\":\"FULL_CREAM\",\"quantity\":2.00,\"unitPrice\":60.00,\"totalPrice\":120.00}]}}"));

        OrderDetailResponse result = client.getOrder(orderId).block();

        assertThat(result.getOrderNumber()).isEqualTo("ORD-2026-000042");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("120.00");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getMilkType()).isEqualTo("FULL_CREAM");
        assertThat(capturedRequest.get().headers().getFirst("X-User-Roles")).isEqualTo("SUPER_ADMIN");
        assertThat(capturedRequest.get().url().toString()).contains("/api/v1/orders/" + orderId);
    }
}
