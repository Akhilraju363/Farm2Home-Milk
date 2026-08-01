package com.farm2home.dashboard.service;

import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.core.dashboard.InventorySummaryResponse;
import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.common.core.dashboard.ProductionSummaryResponse;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.dashboard.client.CustomerServiceClient;
import com.farm2home.dashboard.client.DeliveryServiceClient;
import com.farm2home.dashboard.client.InventoryServiceClient;
import com.farm2home.dashboard.client.NotificationServiceClient;
import com.farm2home.dashboard.client.OrderServiceClient;
import com.farm2home.dashboard.client.PaymentServiceClient;
import com.farm2home.dashboard.client.ProductionServiceClient;
import com.farm2home.dashboard.client.SubscriptionServiceClient;
import com.farm2home.dashboard.dto.response.DashboardSummaryResponse;
import com.farm2home.dashboard.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private CustomerServiceClient customerServiceClient;
    @Mock private SubscriptionServiceClient subscriptionServiceClient;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private DeliveryServiceClient deliveryServiceClient;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private InventoryServiceClient inventoryServiceClient;
    @Mock private ProductionServiceClient productionServiceClient;
    @Mock private NotificationServiceClient notificationServiceClient;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(customerServiceClient, subscriptionServiceClient, orderServiceClient,
                deliveryServiceClient, paymentServiceClient, inventoryServiceClient, productionServiceClient,
                notificationServiceClient, 3000L);
    }

    private void stubAllHappy() {
        when(customerServiceClient.getSummary())
                .thenReturn(Mono.just(CustomerSummaryResponse.builder().totalCustomers(10).build()));
        when(subscriptionServiceClient.getSummary())
                .thenReturn(Mono.just(SubscriptionSummaryResponse.builder().activeSubscriptions(5).build()));
        when(orderServiceClient.getSummary())
                .thenReturn(Mono.just(OrderSummaryResponse.builder().todaysOrders(3).pendingOrders(2).build()));
        when(deliveryServiceClient.getSummary())
                .thenReturn(Mono.just(DeliverySummaryResponse.builder().completedDeliveriesToday(1).build()));
        when(paymentServiceClient.getSummary())
                .thenReturn(Mono.just(PaymentSummaryResponse.builder()
                        .revenueToday(BigDecimal.TEN).revenueThisMonth(BigDecimal.valueOf(100)).build()));
        when(inventoryServiceClient.getSummary(anyInt()))
                .thenReturn(Mono.just(InventorySummaryResponse.builder().lowStockCount(2).topItems(List.of()).build()));
        when(productionServiceClient.getSummary())
                .thenReturn(Mono.just(ProductionSummaryResponse.builder().totalLitersToday(BigDecimal.valueOf(50)).build()));
        when(notificationServiceClient.getRecent(anyInt())).thenReturn(Mono.just(List.of()));
    }

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("all downstream services respond → aggregates every field")
        void happyPath() {
            stubAllHappy();

            DashboardSummaryResponse result = service.getSummary(5, 10);

            assertThat(result.getTotalCustomers()).isEqualTo(10);
            assertThat(result.getActiveSubscriptions()).isEqualTo(5);
            assertThat(result.getTodaysOrders()).isEqualTo(3);
            assertThat(result.getPendingOrders()).isEqualTo(2);
            assertThat(result.getCompletedDeliveriesToday()).isEqualTo(1);
            assertThat(result.getRevenueToday()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(result.getRevenueThisMonth()).isEqualByComparingTo(BigDecimal.valueOf(100));
            assertThat(result.getLowStockProductsCount()).isEqualTo(2);
            assertThat(result.getMilkProductionToday()).isEqualByComparingTo(BigDecimal.valueOf(50));
            assertThat(result.getRecentNotifications()).isEmpty();
        }

        @Test
        @DisplayName("one downstream service errors → that field falls back to a safe default, others unaffected")
        void partialFailure_fallsBackForFailedServiceOnly() {
            when(customerServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("customer-service down")));
            when(subscriptionServiceClient.getSummary())
                    .thenReturn(Mono.just(SubscriptionSummaryResponse.builder().activeSubscriptions(5).build()));
            when(orderServiceClient.getSummary())
                    .thenReturn(Mono.just(OrderSummaryResponse.builder().todaysOrders(3).pendingOrders(2).build()));
            when(deliveryServiceClient.getSummary())
                    .thenReturn(Mono.just(DeliverySummaryResponse.builder().completedDeliveriesToday(1).build()));
            when(paymentServiceClient.getSummary())
                    .thenReturn(Mono.just(PaymentSummaryResponse.builder()
                            .revenueToday(BigDecimal.ZERO).revenueThisMonth(BigDecimal.ZERO).build()));
            when(inventoryServiceClient.getSummary(anyInt()))
                    .thenReturn(Mono.just(InventorySummaryResponse.builder().lowStockCount(0).topItems(List.of()).build()));
            when(productionServiceClient.getSummary())
                    .thenReturn(Mono.just(ProductionSummaryResponse.builder().totalLitersToday(BigDecimal.ZERO).build()));
            when(notificationServiceClient.getRecent(anyInt())).thenReturn(Mono.just(List.of()));

            DashboardSummaryResponse result = service.getSummary(5, 10);

            assertThat(result.getTotalCustomers()).isEqualTo(0);
            assertThat(result.getActiveSubscriptions()).isEqualTo(5);
            assertThat(result.getTodaysOrders()).isEqualTo(3);
        }

        @Test
        @DisplayName("every downstream service errors → returns an all-fallback response instead of throwing")
        void allFailures_neverThrows() {
            when(customerServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("down")));
            when(subscriptionServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("down")));
            when(orderServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("down")));
            when(deliveryServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("down")));
            when(paymentServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("down")));
            when(inventoryServiceClient.getSummary(anyInt())).thenReturn(Mono.error(new RuntimeException("down")));
            when(productionServiceClient.getSummary()).thenReturn(Mono.error(new RuntimeException("down")));
            when(notificationServiceClient.getRecent(anyInt())).thenReturn(Mono.error(new RuntimeException("down")));

            DashboardSummaryResponse result = service.getSummary(5, 10);

            assertThat(result.getTotalCustomers()).isEqualTo(0);
            assertThat(result.getRevenueToday()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getLowStockProducts()).isEmpty();
            assertThat(result.getRecentNotifications()).isEmpty();
        }
    }
}
