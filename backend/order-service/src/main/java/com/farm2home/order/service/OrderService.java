package com.farm2home.order.service;

import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.UUID;

public interface OrderService {

    /** Create a manual ONE_TIME order. */
    OrderResponse createManualOrder(CreateOrderRequest request, UUID customerId);

    /** List orders. {@code null} customerId returns all (admin). */
    Page<OrderResponse> findAll(UUID customerId, Pageable pageable);

    /** Get order by ID. Ownership enforced unless customerId is null (admin). */
    OrderResponse findById(UUID id, UUID customerId);

    /** Get all orders for a specific subscription. */
    Page<OrderResponse> findBySubscription(UUID subscriptionId, Pageable pageable);

    /**
     * Transition order status.
     * Validates allowed transitions; DELIVERY_PARTNER can only move to DELIVERED.
     * actorId identifies who performed the change for audit purposes - customerId is null for
     * admin calls (it means "don't scope the lookup to one customer"), so actorId is passed
     * separately rather than reused for that.
     */
    OrderResponse updateStatus(UUID id, UpdateOrderStatusRequest request, UUID customerId, boolean isAdmin,
            UUID actorId);

    /** Soft-cancel an order. See updateStatus for why actorId is separate from customerId. */
    void cancel(UUID id, UUID customerId, boolean isAdmin, UUID actorId);

    /** Get order summary metrics for the dashboard. */
    OrderSummaryResponse getSummary();

    /** Sales Report: filtered, paginated orders plus summary totals over the same filter set. */
    ReportPage<SalesReportRow, SalesReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            OrderStatus status, UUID customerId, MilkType milkType, Pageable pageable);

    /** Enterprise search: keyword (order number/notes) + status + date range + product filters.
     *  {@code null} customerId returns across all customers (admin), same convention as findAll. */
    Page<OrderResponse> search(UUID customerId, String keyword, LocalDate dateFrom, LocalDate dateTo,
            OrderStatus status, MilkType milkType, Pageable pageable);

    /** Streams orders matching the same filters as {@link #search} to a downloadable file. See
     *  OrderServiceImpl for why this isn't @Transactional. */
    void export(ExportFormat format, OutputStream out, UUID customerId, String keyword, LocalDate dateFrom,
            LocalDate dateTo, OrderStatus status, MilkType milkType, String sortBy, boolean ascending) throws IOException;
}
