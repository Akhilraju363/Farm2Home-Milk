package com.farm2home.delivery.service;

import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.dto.request.DelayAssignmentRequest;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.common.core.analytics.DeliveryPerformancePoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DeliveryAssignmentService {

    AssignmentResponse manualAssign(ManualAssignRequest request);

    AssignmentResponse updateStatus(UUID id, UpdateAssignmentStatusRequest request,
                                    UUID callerId, boolean isAdmin);

    AssignmentResponse markDelayed(UUID id, DelayAssignmentRequest request, UUID callerId, boolean isAdmin);

    AssignmentResponse findById(UUID id, UUID callerId, boolean isAdmin);

    Page<AssignmentResponse> findAll(UUID partnerId, boolean isAdmin, Pageable pageable);

    /** Admin sees every assignment for the order; a DELIVERY_PARTNER sees only assignments that
     *  are theirs (empty list otherwise); any other caller (e.g. CUSTOMER) gets an empty list -
     *  DeliveryAssignment.customerId is now reliably populated for both auto- and manually-created
     *  assignments (see manualAssign()'s order-service backfill), but this endpoint still isn't
     *  exposed to customers at all (no CUSTOMER-facing route in AppRoutes/Sidebar), matching
     *  order/payment ownership's existing "no unverified cross-service trust" convention. */
    List<AssignmentResponse> findByOrderId(UUID orderId, UUID callerId, boolean isAdmin);

    DeliverySummaryResponse getSummary();

    ReportPage<DeliveryReportRow, DeliveryReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            AssignmentStatus status, List<UUID> orderIds, Pageable pageable);

    /** Enterprise search: keyword (partner/route name or area/city) + status + date range +
     *  autoAssigned filters. Partner-scoped like findAll: non-admins only see their own
     *  assignments. autoAssigned is null-safe (omitted = both); used by the admin dashboard's
     *  "Automatically assigned"/"Manually assigned" KPI counts via this same search().totalElements
     *  pattern already used for per-status counts, not a bespoke count endpoint. */
    Page<AssignmentResponse> search(UUID partnerId, boolean isAdmin, String keyword, LocalDate dateFrom,
            LocalDate dateTo, AssignmentStatus status, Boolean autoAssigned, Pageable pageable);

    /** Delivery Performance: total/completed/failed delivery counts bucketed by the requested granularity. */
    TrendSeries<DeliveryPerformancePoint> getPerformanceTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo);
}
