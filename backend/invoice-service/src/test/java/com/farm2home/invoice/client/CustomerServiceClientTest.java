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
 * CustomerServiceClient is a thin WebClient wrapper - the WebClient.Builder is given a stub
 * ExchangeFunction returning a canned ApiResponse body, exercising the real URI-building/
 * header-forwarding/response-mapping code without a network dependency (same technique used
 * by the other cross-service client classes in this package).
 */
class CustomerServiceClientTest {

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
    @DisplayName("getCustomer() parses the response and sends the fixed system identity headers")
    void getCustomer_parsesResponseAndSendsSystemHeaders() {
        UUID customerId = UUID.randomUUID();
        CustomerServiceClient client = new CustomerServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"id\":\"" + customerId + "\",\"firstName\":\"Asha\","
                        + "\"lastName\":\"Rao\",\"mobile\":\"9876543210\",\"email\":\"asha@example.com\"}}"));

        CustomerDetailResponse result = client.getCustomer(customerId).block();

        assertThat(result.getFirstName()).isEqualTo("Asha");
        assertThat(result.getEmail()).isEqualTo("asha@example.com");
        assertThat(capturedRequest.get().headers().getFirst("X-User-Roles")).isEqualTo("SUPER_ADMIN");
        assertThat(capturedRequest.get().url().toString()).contains("/api/v1/customers/" + customerId);
    }

    @Test
    @DisplayName("getDefaultAddress() picks the address flagged as default when several exist")
    void getDefaultAddress_picksFlaggedDefault() {
        UUID customerId = UUID.randomUUID();
        CustomerServiceClient client = new CustomerServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":["
                        + "{\"addressLine1\":\"Old House\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\",\"defaultAddress\":false},"
                        + "{\"addressLine1\":\"New House\",\"city\":\"Mumbai\",\"state\":\"Maharashtra\",\"pincode\":\"400001\",\"defaultAddress\":true}"
                        + "]}"));

        AddressDetailResponse result = client.getDefaultAddress(customerId).block();

        assertThat(result.getAddressLine1()).isEqualTo("New House");
    }

    @Test
    @DisplayName("getDefaultAddress() falls back to the first address when none is flagged default")
    void getDefaultAddress_fallsBackToFirst_whenNoneFlagged() {
        UUID customerId = UUID.randomUUID();
        CustomerServiceClient client = new CustomerServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":["
                        + "{\"addressLine1\":\"Only House\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\",\"defaultAddress\":false}"
                        + "]}"));

        AddressDetailResponse result = client.getDefaultAddress(customerId).block();

        assertThat(result.getAddressLine1()).isEqualTo("Only House");
    }

    @Test
    @DisplayName("getDefaultAddress() resolves empty (not an error) when the downstream call fails")
    void getDefaultAddress_downstreamError_resolvesEmpty() {
        UUID customerId = UUID.randomUUID();
        WebClient.Builder failingBuilder = WebClient.builder().exchangeFunction(request ->
                Mono.error(new UncheckedIOException(new IOException("connection refused"))));
        CustomerServiceClient client = new CustomerServiceClient(failingBuilder);

        AddressDetailResponse result = client.getDefaultAddress(customerId).block();

        assertThat(result).isNull();
    }
}
