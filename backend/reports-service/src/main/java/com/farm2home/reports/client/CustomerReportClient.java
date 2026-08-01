package com.farm2home.reports.client;

import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.dto.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class CustomerReportClient {

    private final WebClient webClient;
    private final RequestHeaderForwarder headerForwarder;

    public CustomerReportClient(WebClient.Builder loadBalancedWebClientBuilder, RequestHeaderForwarder headerForwarder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://customer-service").build();
        this.headerForwarder = headerForwarder;
    }

    public Mono<ReportPage<CustomerReportRow, CustomerReportSummary>> getReport(LocalDate dateFrom, LocalDate dateTo,
            String status, int page, int size) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/customers/reports")
                        .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom))
                        .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo))
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .headers(h -> h.addAll(headerForwarder.forwardable()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ReportPage<CustomerReportRow, CustomerReportSummary>>>() { })
                .map(ApiResponse::getData);
    }
}
