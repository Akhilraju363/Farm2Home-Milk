package com.farm2home.invoice.client;

import com.farm2home.common.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class CustomerServiceClient {

    private final WebClient webClient;

    public CustomerServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://customer-service").build();
    }

    public Mono<CustomerDetailResponse> getCustomer(UUID customerId) {
        return webClient.get()
                .uri("/api/v1/customers/{id}", customerId)
                .headers(SystemIdentityHeaders::apply)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<CustomerDetailResponse>>() { })
                .map(ApiResponse::getData)
                .timeout(Duration.ofSeconds(5));
    }

    /** The customer's default delivery address, if any is set - not a distinct "billing address"
     *  concept, since customer-service has no such field; this is the same default address used
     *  for delivery elsewhere in the platform. */
    public Mono<AddressDetailResponse> getDefaultAddress(UUID customerId) {
        return webClient.get()
                .uri("/api/v1/customers/{id}/addresses", customerId)
                .headers(SystemIdentityHeaders::apply)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<AddressDetailResponse>>>() { })
                .map(ApiResponse::getData)
                .timeout(Duration.ofSeconds(5))
                .mapNotNull(addresses -> addresses.stream()
                        .filter(AddressDetailResponse::isDefaultAddress)
                        .findFirst()
                        .or(() -> addresses.stream().findFirst())
                        .orElse(null))
                .onErrorResume(ex -> {
                    log.warn("Could not resolve address for customer {}: {}", customerId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
