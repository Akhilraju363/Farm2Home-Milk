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

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.UUID;

public interface ReportService {

    ReportPage<SalesReportRow, SalesReportSummary> getSalesReport(LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, String milkType, int page, int size);

    ReportPage<CustomerReportRow, CustomerReportSummary> getCustomerReport(LocalDate dateFrom, LocalDate dateTo,
            String status, int page, int size);

    ReportPage<SubscriptionReportRow, SubscriptionReportSummary> getSubscriptionReport(LocalDate dateFrom,
            LocalDate dateTo, String status, UUID customerId, String milkType, int page, int size);

    ReportPage<InventoryReportRow, InventoryReportSummary> getInventoryReport(LocalDate dateFrom, LocalDate dateTo,
            String txnType, UUID itemId, int page, int size);

    /** {@code farmId} is resolved to its cow ids (via farm-service) before querying
     *  production-service, which has no farm linkage of its own. */
    ReportPage<ProductionReportRow, ProductionReportSummary> getProductionReport(LocalDate dateFrom, LocalDate dateTo,
            String qualityGrade, UUID farmId, int page, int size);

    /** {@code customerId} is resolved to that customer's order ids (via order-service's own
     *  Sales Report) before querying delivery-service, which has no customer linkage of its own. */
    ReportPage<DeliveryReportRow, DeliveryReportSummary> getDeliveryReport(LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, int page, int size);

    ReportPage<PaymentReportRow, PaymentReportSummary> getPaymentReport(LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, int page, int size);

    // ── Export ──────────────────────────────────────────────────────────────────
    // Each streams the same filtered rows as its "get*Report" counterpart to a downloadable
    // file, fetching in bounded pages from the owning service's report client rather than
    // requesting the full dataset in one call - reports-service has no database of its own, so
    // this HTTP-level batching is the equivalent of the direct-DB batch fetch every other
    // exporting service uses.

    void exportSalesReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, String milkType) throws IOException;

    void exportCustomerReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status) throws IOException;

    void exportSubscriptionReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId, String milkType) throws IOException;

    void exportInventoryReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String txnType, UUID itemId) throws IOException;

    void exportProductionReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String qualityGrade, UUID farmId) throws IOException;

    void exportDeliveryReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId) throws IOException;

    void exportPaymentReport(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            String status, UUID customerId) throws IOException;
}
