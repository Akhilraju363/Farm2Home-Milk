package com.farm2home.dashboard.client;

import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.core.dashboard.InventorySummaryResponse;
import com.farm2home.common.core.dashboard.NotificationSummaryItem;
import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.common.core.dashboard.ProductionSummaryResponse;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.web.client.RequestHeaderForwarder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each client is a thin WebClient wrapper - rather than standing up a real HTTP server, the
 * WebClient.Builder is given a stub ExchangeFunction that returns a canned ApiResponse body,
 * which exercises the real URI-building/header-forwarding/response-mapping code in each class
 * (the part that matters) without a network dependency.
 */
class DashboardClientsTest {

    private final RequestHeaderForwarder headerForwarder = new RequestHeaderForwarder();

    private WebClient.Builder stubbedBuilder(String jsonBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build()));
    }

    @Test
    @DisplayName("CustomerServiceClient parses the summary response")
    void customerServiceClient_parsesResponse() {
        CustomerServiceClient client = new CustomerServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"totalCustomers\":10}}"), headerForwarder);

        CustomerSummaryResponse result = client.getSummary().block();

        assertThat(result.getTotalCustomers()).isEqualTo(10);
    }

    @Test
    @DisplayName("SubscriptionServiceClient parses the summary response")
    void subscriptionServiceClient_parsesResponse() {
        SubscriptionServiceClient client = new SubscriptionServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"activeSubscriptions\":5}}"), headerForwarder);

        SubscriptionSummaryResponse result = client.getSummary().block();

        assertThat(result.getActiveSubscriptions()).isEqualTo(5);
    }

    @Test
    @DisplayName("OrderServiceClient parses the summary response")
    void orderServiceClient_parsesResponse() {
        OrderServiceClient client = new OrderServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"todaysOrders\":3,\"pendingOrders\":2}}"), headerForwarder);

        OrderSummaryResponse result = client.getSummary().block();

        assertThat(result.getTodaysOrders()).isEqualTo(3);
        assertThat(result.getPendingOrders()).isEqualTo(2);
    }

    @Test
    @DisplayName("DeliveryServiceClient parses the summary response")
    void deliveryServiceClient_parsesResponse() {
        DeliveryServiceClient client = new DeliveryServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"completedDeliveriesToday\":1}}"), headerForwarder);

        DeliverySummaryResponse result = client.getSummary().block();

        assertThat(result.getCompletedDeliveriesToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("PaymentServiceClient parses the summary response")
    void paymentServiceClient_parsesResponse() {
        PaymentServiceClient client = new PaymentServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"revenueToday\":10,\"revenueThisMonth\":100}}"), headerForwarder);

        PaymentSummaryResponse result = client.getSummary().block();

        assertThat(result.getRevenueToday()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.getRevenueThisMonth()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("InventoryServiceClient sends the limit query param and parses the response")
    void inventoryServiceClient_parsesResponse() {
        InventoryServiceClient client = new InventoryServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"lowStockCount\":2,\"topItems\":[]}}"), headerForwarder);

        InventorySummaryResponse result = client.getSummary(5).block();

        assertThat(result.getLowStockCount()).isEqualTo(2);
        assertThat(result.getTopItems()).isEmpty();
    }

    @Test
    @DisplayName("ProductionServiceClient parses the summary response")
    void productionServiceClient_parsesResponse() {
        ProductionServiceClient client = new ProductionServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":{\"totalLitersToday\":50}}"), headerForwarder);

        ProductionSummaryResponse result = client.getSummary().block();

        assertThat(result.getTotalLitersToday()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    @DisplayName("NotificationServiceClient sends the limit query param and parses the list response")
    void notificationServiceClient_parsesResponse() {
        NotificationServiceClient client = new NotificationServiceClient(
                stubbedBuilder("{\"success\":true,\"data\":[]}"), headerForwarder);

        List<NotificationSummaryItem> result = client.getRecent(10).block();

        assertThat(result).isEmpty();
    }
}
