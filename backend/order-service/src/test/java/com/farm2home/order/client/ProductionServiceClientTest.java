package com.farm2home.order.client;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductionServiceClient is a thin WebClient wrapper around production-service's GET /summary
 * endpoint - rather than a real HTTP server, the WebClient.Builder is given a stub
 * ExchangeFunction returning a canned ApiResponse body, exercising the real URI-building/
 * header-forwarding/response-mapping code without a network dependency (same technique used
 * for the other cross-service client classes added this session).
 */
class ProductionServiceClientTest {

    private final RequestHeaderForwarder headerForwarder = new RequestHeaderForwarder();

    private WebClient.Builder stubbedBuilder(String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build()));
    }

    @Test
    @DisplayName("getDailySummary() parses a non-empty summary list")
    void getDailySummary_parsesResponse() {
        LocalDate date = LocalDate.now();
        ProductionServiceClient client = new ProductionServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":[{\"date\":\"" + date + "\",\"totalLiters\":123.45,\"recordCount\":5}]}"),
                headerForwarder);

        List<DailyProductionResponse> result = client.getDailySummary(date, date).block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalLiters()).isEqualByComparingTo("123.45");
    }

    @Test
    @DisplayName("getDailySummary() parses an empty summary list")
    void getDailySummary_empty() {
        LocalDate date = LocalDate.now();
        ProductionServiceClient client = new ProductionServiceClient(stubbedBuilder(
                "{\"success\":true,\"data\":[]}"), headerForwarder);

        List<DailyProductionResponse> result = client.getDailySummary(date, date).block();

        assertThat(result).isEmpty();
    }
}
