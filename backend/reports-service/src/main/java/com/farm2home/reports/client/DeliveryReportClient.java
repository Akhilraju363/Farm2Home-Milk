package com.farm2home.reports.client;

import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DeliveryReportClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public DeliveryReportClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://delivery-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<ReportPage<DeliveryReportRow, DeliveryReportSummary>> getReport(LocalDate dateFrom, LocalDate dateTo,
            String status, List<UUID> orderIds, int page, int size) {
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/delivery/assignments/reports")
                            .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom))
                            .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo))
                            .queryParamIfPresent("status", Optional.ofNullable(status))
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (orderIds != null && !orderIds.isEmpty()) {
                        uriBuilder.queryParam("orderIds", orderIds.toArray());
                    }
                    return uriBuilder.build();
                })
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ReportPage<DeliveryReportRow, DeliveryReportSummary>>>() { })
                .map(ApiResponse::getData);
    }
}
