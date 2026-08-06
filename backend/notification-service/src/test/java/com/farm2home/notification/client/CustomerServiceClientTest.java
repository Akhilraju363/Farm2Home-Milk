package com.farm2home.notification.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomerServiceClient is a thin WebClient wrapper around customer-service's GET /{id} -
 * rather than a real HTTP server, the WebClient.Builder is given a stub ExchangeFunction
 * returning a canned ApiResponse body, exercising the real URI-building/response-mapping code
 * without a network dependency (same technique as delivery-service's PaymentServiceClientTest).
 */
class CustomerServiceClientTest {

    private WebClient.Builder stubbedBuilder(HttpStatus status, String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build()));
    }

    @Test
    @DisplayName("getContact() parses mobile/email/name from a successful response")
    void getContact_success() {
        CustomerServiceClient client = new CustomerServiceClient(stubbedBuilder(HttpStatus.OK,
                "{\"success\":true,\"data\":{\"firstName\":\"Asha\",\"lastName\":\"Rao\","
                        + "\"mobile\":\"9876543210\",\"email\":\"asha.rao@example.com\"}}"));

        CustomerContactDto result = client.getContact(UUID.randomUUID()).block();

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Asha");
        assertThat(result.getMobile()).isEqualTo("9876543210");
        assertThat(result.getEmail()).isEqualTo("asha.rao@example.com");
    }

    @Test
    @DisplayName("getContact() resolves empty (not an error) when the customer can't be found")
    void getContact_notFound_resolvesEmpty() {
        CustomerServiceClient client = new CustomerServiceClient(stubbedBuilder(HttpStatus.NOT_FOUND,
                "{\"success\":false,\"message\":\"Customer not found\"}"));

        CustomerContactDto result = client.getContact(UUID.randomUUID()).block();

        assertThat(result).isNull();
    }
}
