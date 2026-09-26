package com.farm2home.invoice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentServiceClient is a thin WebClient wrapper - the WebClient.Builder is given a stub
 * ExchangeFunction returning a canned ApiResponse body, exercising the real URI-building/
 * header-forwarding/response-mapping code (including the pickPrimary selection rule) without
 * a network dependency (same technique used by the other cross-service client classes in this
 * package).
 */
class PaymentServiceClientTest {

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
    @DisplayName("getPrimaryPaymentForOrder() picks the SUCCESS attempt over a later failed retry")
    void picksSuccess_overLaterFailedAttempt() {
        UUID orderId = UUID.randomUUID();
        PaymentServiceClient client = new PaymentServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":["
                        + "{\"paymentReference\":\"PAY-1\",\"paymentStatus\":\"SUCCESS\",\"paidAt\":\"2026-08-01T10:00:00\",\"createdAt\":\"2026-08-01T10:00:00\"},"
                        + "{\"paymentReference\":\"PAY-2\",\"paymentStatus\":\"FAILED\",\"createdAt\":\"2026-08-02T10:00:00\"}"
                        + "]}"));

        PaymentDetailResponse result = client.getPrimaryPaymentForOrder(orderId).block();

        assertThat(result.getPaymentReference()).isEqualTo("PAY-1");
        assertThat(capturedRequest.get().headers().getFirst("X-User-Roles")).isEqualTo("SUPER_ADMIN");
        assertThat(capturedRequest.get().url().toString()).contains("/api/v1/payments/order/" + orderId);
    }

    @Test
    @DisplayName("getPrimaryPaymentForOrder() falls back to the most recent attempt of any status when none succeeded")
    void fallsBackToMostRecentAttempt_whenNoneSucceeded() {
        UUID orderId = UUID.randomUUID();
        PaymentServiceClient client = new PaymentServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":["
                        + "{\"paymentReference\":\"PAY-1\",\"paymentStatus\":\"FAILED\",\"createdAt\":\"2026-08-01T10:00:00\"},"
                        + "{\"paymentReference\":\"PAY-2\",\"paymentStatus\":\"PENDING\",\"createdAt\":\"2026-08-02T10:00:00\"}"
                        + "]}"));

        PaymentDetailResponse result = client.getPrimaryPaymentForOrder(orderId).block();

        assertThat(result.getPaymentReference()).isEqualTo("PAY-2");
    }

    @Test
    @DisplayName("getPrimaryPaymentForOrder() resolves empty when the order has no payment attempts")
    void noPaymentAttempts_resolvesEmpty() {
        UUID orderId = UUID.randomUUID();
        PaymentServiceClient client = new PaymentServiceClient(stubbedBuilder("{\"success\":true,\"data\":[]}"));

        PaymentDetailResponse result = client.getPrimaryPaymentForOrder(orderId).block();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getPrimaryPaymentForOrder() resolves empty (not an error) when the downstream call fails")
    void downstreamError_resolvesEmpty() {
        UUID orderId = UUID.randomUUID();
        WebClient.Builder failingBuilder = WebClient.builder().exchangeFunction(request ->
                Mono.error(new UncheckedIOException(new IOException("connection refused"))));
        PaymentServiceClient client = new PaymentServiceClient(failingBuilder);

        PaymentDetailResponse result = client.getPrimaryPaymentForOrder(orderId).block();

        assertThat(result).isNull();
    }
}
