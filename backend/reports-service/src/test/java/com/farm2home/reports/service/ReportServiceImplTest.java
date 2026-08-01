package com.farm2home.reports.service;

import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.InventoryReportRow;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ProductionReportRow;
import com.farm2home.common.core.reports.ProductionReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.reports.client.CustomerReportClient;
import com.farm2home.reports.client.DeliveryReportClient;
import com.farm2home.reports.client.FarmCowResolverClient;
import com.farm2home.reports.client.InventoryReportClient;
import com.farm2home.reports.client.PaymentReportClient;
import com.farm2home.reports.client.ProductionReportClient;
import com.farm2home.reports.client.SalesReportClient;
import com.farm2home.reports.client.SubscriptionReportClient;
import com.farm2home.reports.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private SalesReportClient salesReportClient;
    @Mock private CustomerReportClient customerReportClient;
    @Mock private SubscriptionReportClient subscriptionReportClient;
    @Mock private InventoryReportClient inventoryReportClient;
    @Mock private ProductionReportClient productionReportClient;
    @Mock private DeliveryReportClient deliveryReportClient;
    @Mock private PaymentReportClient paymentReportClient;
    @Mock private FarmCowResolverClient farmCowResolverClient;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(salesReportClient, customerReportClient, subscriptionReportClient,
                inventoryReportClient, productionReportClient, deliveryReportClient, paymentReportClient,
                farmCowResolverClient);
    }

    @Nested
    @DisplayName("getProductionReport()")
    class GetProductionReport {

        @Test
        @DisplayName("no farm filter → calls production-service directly with no cow ids")
        void noFarmFilter_callsDirectly() {
            var expected = ReportPage.<ProductionReportRow, ProductionReportSummary>builder()
                    .content(List.of()).build();
            when(productionReportClient.getReport(any(), any(), any(), eq(null), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getProductionReport(null, null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
            verify(farmCowResolverClient, never()).getCowIdsForFarm(any());
        }

        @Test
        @DisplayName("farm filter resolves to cow ids → passed through to production-service")
        void farmFilter_resolvesAndForwards() {
            UUID farmId = UUID.randomUUID();
            UUID cowId = UUID.randomUUID();
            when(farmCowResolverClient.getCowIdsForFarm(farmId)).thenReturn(Mono.just(List.of(cowId)));
            var expected = ReportPage.<ProductionReportRow, ProductionReportSummary>builder()
                    .content(List.of()).build();
            when(productionReportClient.getReport(any(), any(), any(), eq(List.of(cowId)), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getProductionReport(null, null, null, farmId, 0, 20);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("farm has no cows → returns empty report without calling production-service")
        void farmWithNoCows_shortCircuits() {
            UUID farmId = UUID.randomUUID();
            when(farmCowResolverClient.getCowIdsForFarm(farmId)).thenReturn(Mono.just(List.of()));

            var result = service.getProductionReport(null, null, null, farmId, 0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalRecords()).isZero();
            verify(productionReportClient, never()).getReport(any(), any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("getDeliveryReport()")
    class GetDeliveryReport {

        @Test
        @DisplayName("no customer filter → calls delivery-service directly with no order ids")
        void noCustomerFilter_callsDirectly() {
            var expected = ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder().content(List.of()).build();
            when(deliveryReportClient.getReport(any(), any(), any(), eq(null), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getDeliveryReport(null, null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
            verify(salesReportClient, never()).getReport(any(), any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("customer filter resolves to order ids via Sales Report → passed to delivery-service")
        void customerFilter_resolvesAndForwards() {
            UUID customerId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            var customerOrders = ReportPage.<SalesReportRow, SalesReportSummary>builder()
                    .content(List.of(SalesReportRow.builder().orderId(orderId).build()))
                    .build();
            when(salesReportClient.getReport(eq(null), eq(null), eq(null), eq(customerId), eq(null), eq(0), eq(1000)))
                    .thenReturn(Mono.just(customerOrders));
            var expected = ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder().content(List.of()).build();
            when(deliveryReportClient.getReport(any(), any(), any(), eq(List.of(orderId)), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getDeliveryReport(null, null, null, customerId, 0, 20);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("customer has no orders → returns empty report without calling delivery-service")
        void customerWithNoOrders_shortCircuits() {
            UUID customerId = UUID.randomUUID();
            var customerOrders = ReportPage.<SalesReportRow, SalesReportSummary>builder().content(List.of()).build();
            when(salesReportClient.getReport(eq(null), eq(null), eq(null), eq(customerId), eq(null), eq(0), eq(1000)))
                    .thenReturn(Mono.just(customerOrders));

            var result = service.getDeliveryReport(null, null, null, customerId, 0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalDeliveries()).isZero();
            verify(deliveryReportClient, never()).getReport(any(), any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("pass-through reports")
    class PassThroughReports {

        @Test
        @DisplayName("getSalesReport() delegates directly to SalesReportClient")
        void salesReport() {
            var expected = ReportPage.<SalesReportRow, SalesReportSummary>builder().content(List.of()).build();
            when(salesReportClient.getReport(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getSalesReport(null, null, null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("getPaymentReport() delegates directly to PaymentReportClient")
        void paymentReport() {
            var expected = com.farm2home.common.core.reports.ReportPage
                    .<com.farm2home.common.core.reports.PaymentReportRow,
                            com.farm2home.common.core.reports.PaymentReportSummary>builder()
                    .content(List.of()).build();
            when(paymentReportClient.getReport(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getPaymentReport(null, null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("getCustomerReport() delegates directly to CustomerReportClient")
        void customerReport() {
            var expected = com.farm2home.common.core.reports.ReportPage
                    .<com.farm2home.common.core.reports.CustomerReportRow,
                            com.farm2home.common.core.reports.CustomerReportSummary>builder()
                    .content(List.of()).build();
            when(customerReportClient.getReport(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getCustomerReport(null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("getSubscriptionReport() delegates directly to SubscriptionReportClient")
        void subscriptionReport() {
            var expected = com.farm2home.common.core.reports.ReportPage
                    .<com.farm2home.common.core.reports.SubscriptionReportRow,
                            com.farm2home.common.core.reports.SubscriptionReportSummary>builder()
                    .content(List.of()).build();
            when(subscriptionReportClient.getReport(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getSubscriptionReport(null, null, null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("getInventoryReport() delegates directly to InventoryReportClient")
        void inventoryReport() {
            var expected = com.farm2home.common.core.reports.ReportPage
                    .<com.farm2home.common.core.reports.InventoryReportRow,
                            com.farm2home.common.core.reports.InventoryReportSummary>builder()
                    .content(List.of()).build();
            when(inventoryReportClient.getReport(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(expected));

            var result = service.getInventoryReport(null, null, null, null, 0, 20);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("export*Report()")
    class Export {

        private final ReportPage<SalesReportRow, SalesReportSummary> emptySales =
                ReportPage.<SalesReportRow, SalesReportSummary>builder().content(List.of()).build();
        private final ReportPage<ProductionReportRow, ProductionReportSummary> emptyProduction =
                ReportPage.<ProductionReportRow, ProductionReportSummary>builder().content(List.of()).build();
        private final ReportPage<DeliveryReportRow, DeliveryReportSummary> emptyDelivery =
                ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder().content(List.of()).build();

        @Test
        @DisplayName("exportSalesReport() streams rows fetched from SalesReportClient")
        void exportSalesReport_streamsRows() throws Exception {
            var page = ReportPage.<SalesReportRow, SalesReportSummary>builder()
                    .content(List.of(SalesReportRow.builder().orderId(UUID.randomUUID()).orderNumber("ORD-1")
                            .customerId(UUID.randomUUID()).orderDate(LocalDate.now()).status("DELIVERED")
                            .totalAmount(new BigDecimal("100.00")).build()))
                    .build();
            when(salesReportClient.getReport(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(emptySales));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportSalesReport(ExportFormat.CSV, out, null, null, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("ORD-1");
        }

        @Test
        @DisplayName("exportCustomerReport() streams rows fetched from CustomerReportClient")
        void exportCustomerReport_streamsRows() throws Exception {
            var page = ReportPage.<CustomerReportRow, CustomerReportSummary>builder()
                    .content(List.of(CustomerReportRow.builder().customerId(UUID.randomUUID())
                            .customerCode("CUST-1").name("Jane").mobile("9999999999").status("ACTIVE").build()))
                    .build();
            var empty = ReportPage.<CustomerReportRow, CustomerReportSummary>builder().content(List.of()).build();
            when(customerReportClient.getReport(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(empty));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportCustomerReport(ExportFormat.CSV, out, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("CUST-1");
        }

        @Test
        @DisplayName("exportSubscriptionReport() streams rows fetched from SubscriptionReportClient")
        void exportSubscriptionReport_streamsRows() throws Exception {
            var page = ReportPage.<SubscriptionReportRow, SubscriptionReportSummary>builder()
                    .content(List.of(SubscriptionReportRow.builder().subscriptionId(UUID.randomUUID())
                            .customerId(UUID.randomUUID()).milkType("FULL_CREAM").quantity(new BigDecimal("1.00"))
                            .scheduleType("DAILY").startDate(LocalDate.now()).status("ACTIVE").build()))
                    .build();
            var empty = ReportPage.<SubscriptionReportRow, SubscriptionReportSummary>builder().content(List.of()).build();
            when(subscriptionReportClient.getReport(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(empty));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportSubscriptionReport(ExportFormat.CSV, out, null, null, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("FULL_CREAM");
        }

        @Test
        @DisplayName("exportInventoryReport() streams rows fetched from InventoryReportClient")
        void exportInventoryReport_streamsRows() throws Exception {
            var page = ReportPage.<InventoryReportRow, InventoryReportSummary>builder()
                    .content(List.of(InventoryReportRow.builder().transactionId(UUID.randomUUID())
                            .itemId(UUID.randomUUID()).itemName("Rice Straw").txnType("IN")
                            .quantity(new BigDecimal("10.00")).build()))
                    .build();
            var empty = ReportPage.<InventoryReportRow, InventoryReportSummary>builder().content(List.of()).build();
            when(inventoryReportClient.getReport(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(empty));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportInventoryReport(ExportFormat.CSV, out, null, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("Rice Straw");
        }

        @Test
        @DisplayName("exportPaymentReport() streams rows fetched from PaymentReportClient")
        void exportPaymentReport_streamsRows() throws Exception {
            var page = ReportPage.<PaymentReportRow, PaymentReportSummary>builder()
                    .content(List.of(PaymentReportRow.builder().paymentId(UUID.randomUUID())
                            .orderId(UUID.randomUUID()).customerId(UUID.randomUUID())
                            .amount(new BigDecimal("150.00")).paymentMethod("UPI").paymentStatus("SUCCESS").build()))
                    .build();
            var empty = ReportPage.<PaymentReportRow, PaymentReportSummary>builder().content(List.of()).build();
            when(paymentReportClient.getReport(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(empty));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportPaymentReport(ExportFormat.CSV, out, null, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("UPI");
        }

        @Test
        @DisplayName("exportProductionReport() with no farm filter → calls production-service directly")
        void exportProductionReport_noFarmFilter() throws Exception {
            var page = ReportPage.<ProductionReportRow, ProductionReportSummary>builder()
                    .content(List.of(ProductionReportRow.builder().productionId(UUID.randomUUID())
                            .cowId(UUID.randomUUID()).collectionDate(LocalDate.now()).session("MORNING")
                            .quantityLiters(new BigDecimal("5.00")).qualityGrade("A").build()))
                    .build();
            when(productionReportClient.getReport(any(), any(), any(), eq(null), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(emptyProduction));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportProductionReport(ExportFormat.CSV, out, null, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("MORNING");
            verify(farmCowResolverClient, never()).getCowIdsForFarm(any());
        }

        @Test
        @DisplayName("exportProductionReport() with farm filter → resolves cow ids and forwards")
        void exportProductionReport_farmFilter_resolves() throws Exception {
            UUID farmId = UUID.randomUUID();
            UUID cowId = UUID.randomUUID();
            when(farmCowResolverClient.getCowIdsForFarm(farmId)).thenReturn(Mono.just(List.of(cowId)));
            var page = ReportPage.<ProductionReportRow, ProductionReportSummary>builder()
                    .content(List.of(ProductionReportRow.builder().productionId(UUID.randomUUID())
                            .cowId(cowId).collectionDate(LocalDate.now()).session("EVENING")
                            .quantityLiters(new BigDecimal("4.00")).qualityGrade("B").build()))
                    .build();
            when(productionReportClient.getReport(any(), any(), any(), eq(List.of(cowId)), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(emptyProduction));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportProductionReport(ExportFormat.CSV, out, null, null, null, farmId);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("EVENING");
        }

        @Test
        @DisplayName("exportProductionReport() with a farm that has no cows → writes header only")
        void exportProductionReport_farmWithNoCows_writesHeaderOnly() throws Exception {
            UUID farmId = UUID.randomUUID();
            when(farmCowResolverClient.getCowIdsForFarm(farmId)).thenReturn(Mono.just(List.of()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportProductionReport(ExportFormat.CSV, out, null, null, null, farmId);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8).trim())
                    .isEqualTo("Cow ID,Collection Date,Session,Quantity (Liters),Quality Grade");
            verify(productionReportClient, never()).getReport(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("exportDeliveryReport() with no customer filter → calls delivery-service directly")
        void exportDeliveryReport_noCustomerFilter() throws Exception {
            var page = ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder()
                    .content(List.of(DeliveryReportRow.builder().assignmentId(UUID.randomUUID())
                            .orderId(UUID.randomUUID()).status("DELIVERED").build()))
                    .build();
            when(deliveryReportClient.getReport(any(), any(), any(), eq(null), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(emptyDelivery));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportDeliveryReport(ExportFormat.CSV, out, null, null, null, null);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("DELIVERED");
            verify(salesReportClient, never()).getReport(any(), any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("exportDeliveryReport() with customer filter → resolves order ids via Sales Report and forwards")
        void exportDeliveryReport_customerFilter_resolves() throws Exception {
            UUID customerId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            var customerOrders = ReportPage.<SalesReportRow, SalesReportSummary>builder()
                    .content(List.of(SalesReportRow.builder().orderId(orderId).build()))
                    .build();
            when(salesReportClient.getReport(eq(null), eq(null), eq(null), eq(customerId), eq(null), eq(0), eq(1000)))
                    .thenReturn(Mono.just(customerOrders));
            var page = ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder()
                    .content(List.of(DeliveryReportRow.builder().assignmentId(UUID.randomUUID())
                            .orderId(orderId).status("OUT_FOR_DELIVERY").build()))
                    .build();
            when(deliveryReportClient.getReport(any(), any(), any(), eq(List.of(orderId)), anyInt(), anyInt()))
                    .thenReturn(Mono.just(page), Mono.just(emptyDelivery));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportDeliveryReport(ExportFormat.CSV, out, null, null, null, customerId);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("OUT_FOR_DELIVERY");
        }

        @Test
        @DisplayName("exportDeliveryReport() with a customer that has no orders → writes header only")
        void exportDeliveryReport_customerWithNoOrders_writesHeaderOnly() throws Exception {
            UUID customerId = UUID.randomUUID();
            var customerOrders = ReportPage.<SalesReportRow, SalesReportSummary>builder().content(List.of()).build();
            when(salesReportClient.getReport(eq(null), eq(null), eq(null), eq(customerId), eq(null), eq(0), eq(1000)))
                    .thenReturn(Mono.just(customerOrders));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.exportDeliveryReport(ExportFormat.CSV, out, null, null, null, customerId);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8).trim())
                    .isEqualTo("Order ID,Delivery Partner ID,Status,Assigned At,Delivered At");
            verify(deliveryReportClient, never()).getReport(any(), any(), any(), any(), anyInt(), anyInt());
        }
    }
}
