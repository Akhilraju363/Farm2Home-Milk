package com.farm2home.delivery.client;

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
 * PaymentServiceClient is a thin WebClient wrapper around payment-service's
 * GET /order/{orderId}/payment-exists endpoint - rather than a real HTTP server, the
 * WebClient.Builder is given a stub ExchangeFunction returning a canned ApiResponse body,
 * exercising the real URI-building/header-forwarding/response-mapping code without a network
 * dependency (same technique as payment-service's OrderServiceClientTest).
 */
class PaymentServiceClientTest {

    private final RequestHeaderForwarder headerForwarder = new RequestHeaderForwarder();

    private WebClient.Builder stubbedBuilder(String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build()));
    }

    @Test
    @DisplayName("hasPayableProgress() parses a true response")
    void hasPayableProgress_true() {
        UUID orderId = UUID.randomUUID();
        PaymentServiceClient client = new PaymentServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":true}"), headerForwarder);

        Boolean result = client.hasPayableProgress(orderId).block();

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasPayableProgress() parses a false response")
    void hasPayableProgress_false() {
        UUID orderId = UUID.randomUUID();
        PaymentServiceClient client = new PaymentServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":false}"), headerForwarder);

        Boolean result = client.hasPayableProgress(orderId).block();

        assertThat(result).isFalse();
    }
}
