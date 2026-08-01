package com.farm2home.reports.client;

import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionReportClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public SubscriptionReportClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://subscription-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<ReportPage<SubscriptionReportRow, SubscriptionReportSummary>> getReport(LocalDate dateFrom,
            LocalDate dateTo, String status, UUID customerId, String milkType, int page, int size) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/subscriptions/reports")
                        .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom))
                        .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo))
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .queryParamIfPresent("customerId", Optional.ofNullable(customerId))
                        .queryParamIfPresent("milkType", Optional.ofNullable(milkType))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ReportPage<SubscriptionReportRow, SubscriptionReportSummary>>>() { })
                .map(ApiResponse::getData);
    }
}
