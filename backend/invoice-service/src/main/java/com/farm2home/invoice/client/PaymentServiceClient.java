package com.farm2home.invoice.client;

import com.farm2home.common.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class PaymentServiceClient {

    private final WebClient webClient;

    public PaymentServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://payment-service").build();
    }

    /** An order can have more than one payment attempt (an earlier one failed and the customer
     *  retried) - the invoice's payment section shows the one that actually matters: the most
     *  recent SUCCESS if any, else the most recent attempt of any status, else nothing (an
     *  invoice can be generated before payment succeeds, since generation is manual/admin-
     *  triggered and not gated on payment status per the chosen design). */
    public Mono<PaymentDetailResponse> getPrimaryPaymentForOrder(UUID orderId) {
        return webClient.get()
                .uri("/api/v1/payments/order/{orderId}", orderId)
                .headers(SystemIdentityHeaders::apply)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<PaymentDetailResponse>>>() { })
                .map(ApiResponse::getData)
                .timeout(Duration.ofSeconds(5))
                .mapNotNull(this::pickPrimary)
                .onErrorResume(ex -> {
                    log.warn("Could not resolve payment info for order {}: {}", orderId, ex.getMessage());
                    return Mono.empty();
                });
    }

    private PaymentDetailResponse pickPrimary(List<PaymentDetailResponse> payments) {
        if (payments == null || payments.isEmpty()) return null;
        Optional<PaymentDetailResponse> success = payments.stream()
                .filter(p -> "SUCCESS".equals(p.getPaymentStatus()))
                .max(Comparator.comparing(PaymentDetailResponse::getPaidAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return success.orElseGet(() -> payments.stream()
                .max(Comparator.comparing(PaymentDetailResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null));
    }
}
