package com.farm2home.subscription.service;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.SubscriptionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.UUID;

public interface SubscriptionService {

    /**
     * Create a subscription for the given customer.
     */
    SubscriptionResponse create(CreateSubscriptionRequest request, UUID customerId);

    /**
     * List subscriptions. Pass {@code null} as {@code customerId} to return all (admin use).
     */
    Page<SubscriptionResponse> findAll(UUID customerId, Pageable pageable);

    /**
     * Get a single subscription.
     * Ownership is enforced: non-null {@code customerId} must own the record.
     */
    SubscriptionResponse findById(UUID id, UUID customerId);

    /**
     * Update mutable fields of an ACTIVE subscription.
     */
    SubscriptionResponse update(UUID id, UpdateSubscriptionRequest request, UUID customerId);

    /**
     * Soft-cancel a subscription (sets status = CANCELLED).
     */
    void cancel(UUID id, UUID customerId);

    /**
     * Pause an ACTIVE subscription until the requested date.
     */
    SubscriptionResponse pause(UUID id, PauseSubscriptionRequest request, UUID customerId);

    /**
     * Resume a PAUSED subscription immediately.
     */
    SubscriptionResponse resume(UUID id, UUID customerId);

    /**
     * Get subscription summary metrics for the dashboard.
     */
    SubscriptionSummaryResponse getSummary();

    /**
     * Filtered, paginated Subscription Report plus summary totals.
     */
    ReportPage<SubscriptionReportRow, SubscriptionReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            SubscriptionStatus status, UUID customerId, MilkType milkType, Pageable pageable);

    /** Enterprise search: keyword (milk/schedule type) + status + date range + customer filters.
     *  {@code null} customerId returns across all customers (admin), same convention as findAll. */
    Page<SubscriptionResponse> search(UUID customerId, String keyword, LocalDate dateFrom, LocalDate dateTo,
            SubscriptionStatus status, MilkType milkType, Pageable pageable);

    /** Streams subscriptions matching the same filters as {@link #search} to a downloadable file.
     *  See SubscriptionServiceImpl for why this isn't @Transactional. */
    void export(ExportFormat format, OutputStream out, UUID customerId, String keyword, LocalDate dateFrom,
            LocalDate dateTo, SubscriptionStatus status, MilkType milkType, String sortBy, boolean ascending) throws IOException;

    /** Subscription Trend: count of new subscriptions bucketed by the requested granularity. */
    TrendSeries<SubscriptionTrendPoint> getSubscriptionTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo);
}
