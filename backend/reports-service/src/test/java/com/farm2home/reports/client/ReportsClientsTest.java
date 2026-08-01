package com.farm2home.reports.client;

import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ProductionReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.web.client.RequestHeaderForwarder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each client is a thin WebClient wrapper around one owning service's /reports endpoint - rather
 * than a real HTTP server, the WebClient.Builder is given a stub ExchangeFunction returning a
 * canned ApiResponse body, exercising the real URI-building/header-forwarding/response-mapping
 * code without a network dependency (same technique as dashboard-service's client tests).
 */
class ReportsClientsTest {

    private final RequestHeaderForwarder headerForwarder = new RequestHeaderForwarder();

    private WebClient.Builder stubbedBuilder(String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build()));
    }

    @Test
    @DisplayName("SalesReportClient parses the report response")
    void salesReportClient_parsesResponse() {
        SalesReportClient client = new SalesReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalOrders\":3,\"totalRevenue\":150.00}}}"), headerForwarder);

        ReportPage<?, SalesReportSummary> result = client.getReport(null, null, null, null, null, 0, 20).block();

        assertThat(result.getSummary().getTotalOrders()).isEqualTo(3);
    }

    @Test
    @DisplayName("CustomerReportClient parses the report response")
    void customerReportClient_parsesResponse() {
        CustomerReportClient client = new CustomerReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalCustomers\":5,\"activeCustomers\":4,"
                        + "\"inactiveCustomers\":1,\"suspendedCustomers\":0}}}"), headerForwarder);

        ReportPage<?, CustomerReportSummary> result = client.getReport(null, null, null, 0, 20).block();

        assertThat(result.getSummary().getTotalCustomers()).isEqualTo(5);
    }

    @Test
    @DisplayName("SubscriptionReportClient parses the report response")
    void subscriptionReportClient_parsesResponse() {
        SubscriptionReportClient client = new SubscriptionReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalSubscriptions\":2,\"activeSubscriptions\":2,"
                        + "\"totalQuantity\":3.5}}}"), headerForwarder);

        ReportPage<?, SubscriptionReportSummary> result =
                client.getReport(null, null, null, null, null, 0, 20).block();

        assertThat(result.getSummary().getTotalSubscriptions()).isEqualTo(2);
    }

    @Test
    @DisplayName("InventoryReportClient parses the report response")
    void inventoryReportClient_parsesResponse() {
        InventoryReportClient client = new InventoryReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalTransactions\":7,\"totalInQuantity\":10,"
                        + "\"totalOutQuantity\":4}}}"), headerForwarder);

        ReportPage<?, InventoryReportSummary> result = client.getReport(null, null, null, null, 0, 20).block();

        assertThat(result.getSummary().getTotalTransactions()).isEqualTo(7);
    }

    @Test
    @DisplayName("ProductionReportClient sends cowIds and parses the report response")
    void productionReportClient_parsesResponse() {
        ProductionReportClient client = new ProductionReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalRecords\":9,\"totalLiters\":45.5}}}"), headerForwarder);

        ReportPage<?, ProductionReportSummary> result =
                client.getReport(null, null, null, List.of(UUID.randomUUID()), 0, 20).block();

        assertThat(result.getSummary().getTotalRecords()).isEqualTo(9);
    }

    @Test
    @DisplayName("DeliveryReportClient sends orderIds and parses the report response")
    void deliveryReportClient_parsesResponse() {
        DeliveryReportClient client = new DeliveryReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalDeliveries\":6,\"completedCount\":5,"
                        + "\"failedCount\":1}}}"), headerForwarder);

        ReportPage<?, DeliveryReportSummary> result =
                client.getReport(null, null, null, List.of(UUID.randomUUID()), 0, 20).block();

        assertThat(result.getSummary().getTotalDeliveries()).isEqualTo(6);
    }

    @Test
    @DisplayName("PaymentReportClient parses the report response")
    void paymentReportClient_parsesResponse() {
        PaymentReportClient client = new PaymentReportClient(stubbedBuilder(
                "{\"success\":true,\"data\":{\"content\":[],\"pageNumber\":0,\"pageSize\":20,\"totalElements\":0,"
                        + "\"totalPages\":0,\"summary\":{\"totalPayments\":4,\"totalAmount\":800.00,"
                        + "\"successAmount\":600.00}}}"), headerForwarder);

        ReportPage<?, PaymentReportSummary> result = client.getReport(null, null, null, null, 0, 20).block();

        assertThat(result.getSummary().getTotalPayments()).isEqualTo(4);
    }

    @Test
    @DisplayName("FarmCowResolverClient parses the cow id list response")
    void farmCowResolverClient_parsesResponse() {
        UUID cowId = UUID.randomUUID();
        FarmCowResolverClient client = new FarmCowResolverClient(
                stubbedBuilder("{\"success\":true,\"data\":[\"" + cowId + "\"]}"), headerForwarder);

        List<UUID> result = client.getCowIdsForFarm(UUID.randomUUID()).block();

        assertThat(result).containsExactly(cowId);
    }
}
