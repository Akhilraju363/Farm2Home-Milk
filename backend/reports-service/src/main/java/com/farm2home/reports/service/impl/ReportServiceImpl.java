package com.farm2home.reports.service.impl;

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
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.export.TabularExporterFactory;
import com.farm2home.reports.client.CustomerReportClient;
import com.farm2home.reports.client.DeliveryReportClient;
import com.farm2home.reports.client.FarmCowResolverClient;
import com.farm2home.reports.client.InventoryReportClient;
import com.farm2home.reports.client.PaymentReportClient;
import com.farm2home.reports.client.ProductionReportClient;
import com.farm2home.reports.client.SalesReportClient;
import com.farm2home.reports.client.SubscriptionReportClient;
import com.farm2home.reports.service.ReportService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

@Service
public class ReportServiceImpl implements ReportService {

    /** Upper bound when resolving "all orders for a customer" / "all cows for a farm" as filter
     *  input for another report - generous enough that no real customer/farm in this domain
     *  would exceed it, without fetching truly unbounded result sets. */
    private static final int RESOLUTION_PAGE_SIZE = 1000;

    private final SalesReportClient salesReportClient;
    private final CustomerReportClient customerReportClient;
    private final SubscriptionReportClient subscriptionReportClient;
    private final InventoryReportClient inventoryReportClient;
    private final ProductionReportClient productionReportClient;
    private final DeliveryReportClient deliveryReportClient;
    private final PaymentReportClient paymentReportClient;
    private final FarmCowResolverClient farmCowResolverClient;

    public ReportServiceImpl(SalesReportClient salesReportClient, CustomerReportClient customerReportClient,
            SubscriptionReportClient subscriptionReportClient, InventoryReportClient inventoryReportClient,
            ProductionReportClient productionReportClient, DeliveryReportClient deliveryReportClient,
            PaymentReportClient paymentReportClient, FarmCowResolverClient farmCowResolverClient) {
        this.salesReportClient = salesReportClient;
        this.customerReportClient = customerReportClient;
        this.subscriptionReportClient = subscriptionReportClient;
        this.inventoryReportClient = inventoryReportClient;
        this.productionReportClient = productionReportClient;
        this.deliveryReportClient = deliveryReportClient;
        this.paymentReportClient = paymentReportClient;
        this.farmCowResolverClient = farmCowResolverClient;
    }

    @Override
    public ReportPage<SalesReportRow, SalesReportSummary> getSalesReport(LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, String milkType, int page, int size) {
        return salesReportClient.getReport(dateFrom, dateTo, status, customerId, milkType, page, size).block();
    }

    @Override
    public ReportPage<CustomerReportRow, CustomerReportSummary> getCustomerReport(LocalDate dateFrom, LocalDate dateTo,
            String status, int page, int size) {
        return customerReportClient.getReport(dateFrom, dateTo, status, page, size).block();
    }

    @Override
    public ReportPage<SubscriptionReportRow, SubscriptionReportSummary> getSubscriptionReport(LocalDate dateFrom,
            LocalDate dateTo, String status, UUID customerId, String milkType, int page, int size) {
        return subscriptionReportClient.getReport(dateFrom, dateTo, status, customerId, milkType, page, size).block();
    }

    @Override
    public ReportPage<InventoryReportRow, InventoryReportSummary> getInventoryReport(LocalDate dateFrom,
            LocalDate dateTo, String txnType, UUID itemId, int page, int size) {
        return inventoryReportClient.getReport(dateFrom, dateTo, txnType, itemId, page, size).block();
    }

    @Override
    public ReportPage<ProductionReportRow, ProductionReportSummary> getProductionReport(LocalDate dateFrom,
            LocalDate dateTo, String qualityGrade, UUID farmId, int page, int size) {
        if (farmId == null) {
            return productionReportClient.getReport(dateFrom, dateTo, qualityGrade, null, page, size).block();
        }
        List<UUID> cowIds = farmCowResolverClient.getCowIdsForFarm(farmId).block();
        if (cowIds == null || cowIds.isEmpty()) {
            return emptyProductionReport(page, size);
        }
        return productionReportClient.getReport(dateFrom, dateTo, qualityGrade, cowIds, page, size).block();
    }

    @Override
    public ReportPage<DeliveryReportRow, DeliveryReportSummary> getDeliveryReport(LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, int page, int size) {
        if (customerId == null) {
            return deliveryReportClient.getReport(dateFrom, dateTo, status, null, page, size).block();
        }
        ReportPage<SalesReportRow, SalesReportSummary> customerOrders =
                salesReportClient.getReport(null, null, null, customerId, null, 0, RESOLUTION_PAGE_SIZE).block();
        List<UUID> orderIds = customerOrders == null ? List.of()
                : customerOrders.getContent().stream().map(SalesReportRow::getOrderId).toList();
        if (orderIds.isEmpty()) {
            return emptyDeliveryReport(page, size);
        }
        return deliveryReportClient.getReport(dateFrom, dateTo, status, orderIds, page, size).block();
    }

    @Override
    public ReportPage<PaymentReportRow, PaymentReportSummary> getPaymentReport(LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, int page, int size) {
        return paymentReportClient.getReport(dateFrom, dateTo, status, customerId, page, size).block();
    }

    private ReportPage<ProductionReportRow, ProductionReportSummary> emptyProductionReport(int page, int size) {
        return ReportPage.<ProductionReportRow, ProductionReportSummary>builder()
                .content(List.of()).pageNumber(page).pageSize(size).totalElements(0).totalPages(0)
                .summary(ProductionReportSummary.builder().totalRecords(0).totalLiters(BigDecimal.ZERO).build())
                .build();
    }

    private ReportPage<DeliveryReportRow, DeliveryReportSummary> emptyDeliveryReport(int page, int size) {
        return ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder()
                .content(List.of()).pageNumber(page).pageSize(size).totalElements(0).totalPages(0)
                .summary(DeliveryReportSummary.builder().totalDeliveries(0).completedCount(0).failedCount(0).build())
                .build();
    }

    // ── Export ──────────────────────────────────────────────────────────────────

    @Override
    public void exportSalesReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, String milkType) throws IOException {
        List<ExportColumn<SalesReportRow>> columns = List.of(
                new ExportColumn<>("Order Number", SalesReportRow::getOrderNumber),
                new ExportColumn<>("Customer ID", r -> r.getCustomerId().toString()),
                new ExportColumn<>("Order Date", r -> r.getOrderDate().toString()),
                new ExportColumn<>("Status", SalesReportRow::getStatus),
                new ExportColumn<>("Total Amount", r -> r.getTotalAmount().toString()));
        streamReport(out, format, columns, (page, size) ->
                salesReportClient.getReport(dateFrom, dateTo, status, customerId, milkType, page, size).block());
    }

    @Override
    public void exportCustomerReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status) throws IOException {
        List<ExportColumn<CustomerReportRow>> columns = List.of(
                new ExportColumn<>("Customer Code", CustomerReportRow::getCustomerCode),
                new ExportColumn<>("Name", CustomerReportRow::getName),
                new ExportColumn<>("Mobile", CustomerReportRow::getMobile),
                new ExportColumn<>("Email", r -> r.getEmail() == null ? "" : r.getEmail()),
                new ExportColumn<>("Status", CustomerReportRow::getStatus),
                new ExportColumn<>("Created At", r -> r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()));
        streamReport(out, format, columns, (page, size) ->
                customerReportClient.getReport(dateFrom, dateTo, status, page, size).block());
    }

    @Override
    public void exportSubscriptionReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, String milkType) throws IOException {
        List<ExportColumn<SubscriptionReportRow>> columns = List.of(
                new ExportColumn<>("Subscription ID", r -> r.getSubscriptionId().toString()),
                new ExportColumn<>("Customer ID", r -> r.getCustomerId().toString()),
                new ExportColumn<>("Milk Type", SubscriptionReportRow::getMilkType),
                new ExportColumn<>("Quantity", r -> r.getQuantity().toString()),
                new ExportColumn<>("Schedule Type", SubscriptionReportRow::getScheduleType),
                new ExportColumn<>("Start Date", r -> r.getStartDate().toString()),
                new ExportColumn<>("End Date", r -> r.getEndDate() == null ? "" : r.getEndDate().toString()),
                new ExportColumn<>("Status", SubscriptionReportRow::getStatus));
        streamReport(out, format, columns, (page, size) ->
                subscriptionReportClient.getReport(dateFrom, dateTo, status, customerId, milkType, page, size).block());
    }

    @Override
    public void exportInventoryReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String txnType, UUID itemId) throws IOException {
        List<ExportColumn<InventoryReportRow>> columns = List.of(
                new ExportColumn<>("Item Name", InventoryReportRow::getItemName),
                new ExportColumn<>("Transaction Type", InventoryReportRow::getTxnType),
                new ExportColumn<>("Quantity", r -> r.getQuantity().toString()),
                new ExportColumn<>("Transacted At", r -> r.getTransactedAt() == null ? "" : r.getTransactedAt().toString()));
        streamReport(out, format, columns, (page, size) ->
                inventoryReportClient.getReport(dateFrom, dateTo, txnType, itemId, page, size).block());
    }

    @Override
    public void exportProductionReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String qualityGrade, UUID farmId) throws IOException {
        List<ExportColumn<ProductionReportRow>> columns = List.of(
                new ExportColumn<>("Cow ID", r -> r.getCowId().toString()),
                new ExportColumn<>("Collection Date", r -> r.getCollectionDate().toString()),
                new ExportColumn<>("Session", ProductionReportRow::getSession),
                new ExportColumn<>("Quantity (Liters)", r -> r.getQuantityLiters().toString()),
                new ExportColumn<>("Quality Grade", ProductionReportRow::getQualityGrade));

        if (farmId == null) {
            streamReport(out, format, columns, (page, size) ->
                    productionReportClient.getReport(dateFrom, dateTo, qualityGrade, null, page, size).block());
            return;
        }
        List<UUID> cowIds = farmCowResolverClient.getCowIdsForFarm(farmId).block();
        if (cowIds == null || cowIds.isEmpty()) {
            writeEmpty(out, format, columns);
            return;
        }
        streamReport(out, format, columns, (page, size) ->
                productionReportClient.getReport(dateFrom, dateTo, qualityGrade, cowIds, page, size).block());
    }

    @Override
    public void exportDeliveryReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId) throws IOException {
        List<ExportColumn<DeliveryReportRow>> columns = List.of(
                new ExportColumn<>("Order ID", r -> r.getOrderId().toString()),
                new ExportColumn<>("Delivery Partner ID", r -> r.getDeliveryPartnerId() == null ? "" : r.getDeliveryPartnerId().toString()),
                new ExportColumn<>("Status", DeliveryReportRow::getStatus),
                new ExportColumn<>("Assigned At", r -> r.getAssignedAt() == null ? "" : r.getAssignedAt().toString()),
                new ExportColumn<>("Delivered At", r -> r.getDeliveredAt() == null ? "" : r.getDeliveredAt().toString()));

        if (customerId == null) {
            streamReport(out, format, columns, (page, size) ->
                    deliveryReportClient.getReport(dateFrom, dateTo, status, null, page, size).block());
            return;
        }
        ReportPage<SalesReportRow, SalesReportSummary> customerOrders =
                salesReportClient.getReport(null, null, null, customerId, null, 0, RESOLUTION_PAGE_SIZE).block();
        List<UUID> orderIds = customerOrders == null ? List.of()
                : customerOrders.getContent().stream().map(SalesReportRow::getOrderId).toList();
        if (orderIds.isEmpty()) {
            writeEmpty(out, format, columns);
            return;
        }
        streamReport(out, format, columns, (page, size) ->
                deliveryReportClient.getReport(dateFrom, dateTo, status, orderIds, page, size).block());
    }

    @Override
    public void exportPaymentReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId) throws IOException {
        List<ExportColumn<PaymentReportRow>> columns = List.of(
                new ExportColumn<>("Order ID", r -> r.getOrderId().toString()),
                new ExportColumn<>("Customer ID", r -> r.getCustomerId().toString()),
                new ExportColumn<>("Amount", r -> r.getAmount().toString()),
                new ExportColumn<>("Payment Method", PaymentReportRow::getPaymentMethod),
                new ExportColumn<>("Payment Status", PaymentReportRow::getPaymentStatus),
                new ExportColumn<>("Paid At", r -> r.getPaidAt() == null ? "" : r.getPaidAt().toString()),
                new ExportColumn<>("Created At", r -> r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()));
        streamReport(out, format, columns, (page, size) ->
                paymentReportClient.getReport(dateFrom, dateTo, status, customerId, page, size).block());
    }

    /** Shared by every export* method: pulls rows page-by-page from the given fetcher (each call
     *  a separate HTTP round-trip to the owning service's own report endpoint) and hands them to
     *  the requested TabularExporter, so the full report result set is never held in memory at
     *  once - just one page at a time. */
    private <T, S> void streamReport(OutputStream out, ExportFormat format, List<ExportColumn<T>> columns,
            BiFunction<Integer, Integer, ReportPage<T, S>> pageFetcher) throws IOException {
        BatchSupplier<T> supplier = (page, size) -> {
            ReportPage<T, S> result = pageFetcher.apply(page, size);
            return result == null ? List.of() : result.getContent();
        };
        TabularExporterFactory.<T>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }

    private <T> void writeEmpty(OutputStream out, ExportFormat format, List<ExportColumn<T>> columns) throws IOException {
        TabularExporterFactory.<T>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, (page, size) -> List.of());
    }
}
