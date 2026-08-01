package com.farm2home.dashboard.service.impl;

import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.core.dashboard.InventorySummaryResponse;
import com.farm2home.common.core.dashboard.NotificationSummaryItem;
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
import com.farm2home.dashboard.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Fans out to all 8 owning services concurrently (Mono.zip) rather than sequentially, so total
 * latency is bounded by the slowest single call, not the sum of all of them. Each call carries
 * its own timeout + fallback: one slow/unavailable service degrades that one field to a safe
 * default instead of failing the whole dashboard - see individual on*Failed loggers.
 */
@Service
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final CustomerServiceClient customerServiceClient;
    private final SubscriptionServiceClient subscriptionServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final ProductionServiceClient productionServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final Duration downstreamTimeout;

    public DashboardServiceImpl(CustomerServiceClient customerServiceClient,
            SubscriptionServiceClient subscriptionServiceClient,
            OrderServiceClient orderServiceClient,
            DeliveryServiceClient deliveryServiceClient,
            PaymentServiceClient paymentServiceClient,
            InventoryServiceClient inventoryServiceClient,
            ProductionServiceClient productionServiceClient,
            NotificationServiceClient notificationServiceClient,
            @Value("${dashboard.downstream-timeout-ms:3000}") long downstreamTimeoutMs) {
        this.customerServiceClient = customerServiceClient;
        this.subscriptionServiceClient = subscriptionServiceClient;
        this.orderServiceClient = orderServiceClient;
        this.deliveryServiceClient = deliveryServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.inventoryServiceClient = inventoryServiceClient;
        this.productionServiceClient = productionServiceClient;
        this.notificationServiceClient = notificationServiceClient;
        this.downstreamTimeout = Duration.ofMillis(downstreamTimeoutMs);
    }

    @Override
    public DashboardSummaryResponse getSummary(int lowStockLimit, int recentNotificationsLimit) {
        Mono<CustomerSummaryResponse> customers = withFallback(customerServiceClient.getSummary(),
                "customer-service", CustomerSummaryResponse.builder().totalCustomers(0).build());

        Mono<SubscriptionSummaryResponse> subscriptions = withFallback(subscriptionServiceClient.getSummary(),
                "subscription-service", SubscriptionSummaryResponse.builder().activeSubscriptions(0).build());

        Mono<OrderSummaryResponse> orders = withFallback(orderServiceClient.getSummary(),
                "order-service", OrderSummaryResponse.builder().todaysOrders(0).pendingOrders(0).build());

        Mono<DeliverySummaryResponse> deliveries = withFallback(deliveryServiceClient.getSummary(),
                "delivery-service", DeliverySummaryResponse.builder().completedDeliveriesToday(0).build());

        Mono<PaymentSummaryResponse> payments = withFallback(paymentServiceClient.getSummary(),
                "payment-service", PaymentSummaryResponse.builder()
                        .revenueToday(BigDecimal.ZERO).revenueThisMonth(BigDecimal.ZERO).build());

        Mono<InventorySummaryResponse> inventory = withFallback(inventoryServiceClient.getSummary(lowStockLimit),
                "inventory-service", InventorySummaryResponse.builder()
                        .lowStockCount(0).topItems(List.of()).build());

        Mono<ProductionSummaryResponse> production = withFallback(productionServiceClient.getSummary(),
                "production-service", ProductionSummaryResponse.builder().totalLitersToday(BigDecimal.ZERO).build());

        Mono<List<NotificationSummaryItem>> notifications = withFallback(
                notificationServiceClient.getRecent(recentNotificationsLimit), "notification-service", List.of());

        return Mono.zip(customers, subscriptions, orders, deliveries, payments, inventory, production, notifications)
                .map(tuple -> DashboardSummaryResponse.builder()
                        .totalCustomers(tuple.getT1().getTotalCustomers())
                        .activeSubscriptions(tuple.getT2().getActiveSubscriptions())
                        .todaysOrders(tuple.getT3().getTodaysOrders())
                        .pendingOrders(tuple.getT3().getPendingOrders())
                        .completedDeliveriesToday(tuple.getT4().getCompletedDeliveriesToday())
                        .revenueToday(tuple.getT5().getRevenueToday())
                        .revenueThisMonth(tuple.getT5().getRevenueThisMonth())
                        .lowStockProductsCount(tuple.getT6().getLowStockCount())
                        .lowStockProducts(tuple.getT6().getTopItems())
                        .milkProductionToday(tuple.getT7().getTotalLitersToday())
                        .recentNotifications(tuple.getT8())
                        .build())
                .block();
    }

    private <T> Mono<T> withFallback(Mono<T> source, String serviceName, T fallback) {
        return source
                .timeout(downstreamTimeout)
                .onErrorResume(ex -> {
                    log.warn("Dashboard: {} summary unavailable, using fallback value ({}: {})",
                            serviceName, ex.getClass().getSimpleName(), ex.getMessage());
                    return Mono.just(fallback);
                });
    }
}
