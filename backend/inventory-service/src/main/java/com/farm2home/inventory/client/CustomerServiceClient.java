package com.farm2home.inventory.client;

import com.farm2home.common.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(ex -> {
                    log.warn("Could not resolve customer {}: {}", customerId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
