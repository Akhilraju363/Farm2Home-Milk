package com.farm2home.reports.client;

import com.farm2home.common.core.reports.ProductionReportRow;
import com.farm2home.common.core.reports.ProductionReportSummary;
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
public class ProductionReportClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public ProductionReportClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://production-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<ReportPage<ProductionReportRow, ProductionReportSummary>> getReport(LocalDate dateFrom, LocalDate dateTo,
            String qualityGrade, List<UUID> cowIds, int page, int size) {
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/productions/reports")
                            .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom))
                            .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo))
                            .queryParamIfPresent("qualityGrade", Optional.ofNullable(qualityGrade))
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (cowIds != null && !cowIds.isEmpty()) {
                        uriBuilder.queryParam("cowIds", cowIds.toArray());
                    }
                    return uriBuilder.build();
                })
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ReportPage<ProductionReportRow, ProductionReportSummary>>>() { })
                .map(ApiResponse::getData);
    }
}
