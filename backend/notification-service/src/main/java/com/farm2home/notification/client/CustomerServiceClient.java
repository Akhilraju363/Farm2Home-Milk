package com.farm2home.notification.client;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves a customer's contact info (mobile/email/name) by id - needed because most Kafka
 * events (Order/Payment/Subscription/Delivery) only carry a customerId, not the recipient's
 * actual mobile/email, so NotificationServiceImpl.process() has nothing to send SMS/EMAIL to
 * without this lookup (PUSH addresses by customerId directly and doesn't need it).
 *
 * This runs on a Kafka listener thread, not an inbound HTTP request, so unlike every other
 * cross-service client in this codebase there is no caller identity to forward via
 * RequestHeaderForwarder - it would be empty and customer-service would reject the call as
 * unauthenticated. GET /{id} on customer-service has no role restriction beyond "is there a
 * caller at all" (GatewayHeaderAuthFilter), so a fixed, clearly-labelled system identity is
 * enough - this never reaches a write path or a role-gated endpoint.
 */
@Component
@Slf4j
public class CustomerServiceClient {

    private static final String SYSTEM_CALLER_ID = "00000000-0000-0000-0000-000000000000";
    private static final String SYSTEM_CALLER_MOBILE = "notification-service";

    private final WebClient webClient;

    public CustomerServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://customer-service").build();
    }

    public Mono<CustomerContactDto> getContact(UUID customerId) {
        return webClient.get()
                .uri("/api/v1/customers/{id}", customerId)
                .headers(this::addSystemIdentity)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<CustomerContactDto>>() { })
                .map(ApiResponse::getData)
                .onErrorResume(ex -> {
                    log.warn("Could not resolve contact info for customer {}: {}", customerId, ex.getMessage());
                    return Mono.empty();
                });
    }

    private void addSystemIdentity(HttpHeaders headers) {
        headers.add(HeaderConstants.X_USER_ID, SYSTEM_CALLER_ID);
        headers.add(HeaderConstants.X_USER_MOBILE, SYSTEM_CALLER_MOBILE);
    }
}
