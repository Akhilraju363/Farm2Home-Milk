package com.farm2home.reports.client;

import com.farm2home.common.core.reports.InventoryReportRow;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
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
public class InventoryReportClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public InventoryReportClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://inventory-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<ReportPage<InventoryReportRow, InventoryReportSummary>> getReport(LocalDate dateFrom, LocalDate dateTo,
            String txnType, UUID itemId, int page, int size) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/inventory/transactions/reports")
                        .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom))
                        .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo))
                        .queryParamIfPresent("txnType", Optional.ofNullable(txnType))
                        .queryParamIfPresent("itemId", Optional.ofNullable(itemId))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ReportPage<InventoryReportRow, InventoryReportSummary>>>() { })
                .map(ApiResponse::getData);
    }
}
