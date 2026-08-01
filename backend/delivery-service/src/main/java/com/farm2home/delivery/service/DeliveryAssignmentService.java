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

    List<AssignmentResponse> findByOrderId(UUID orderId);

    DeliverySummaryResponse getSummary();

    ReportPage<DeliveryReportRow, DeliveryReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            AssignmentStatus status, List<UUID> orderIds, Pageable pageable);

    /** Enterprise search: keyword (partner/route name or area/city) + status + date range filters.
     *  Partner-scoped like findAll: non-admins only see their own assignments. */
    Page<AssignmentResponse> search(UUID partnerId, boolean isAdmin, String keyword, LocalDate dateFrom,
            LocalDate dateTo, AssignmentStatus status, Pageable pageable);

    /** Delivery Performance: total/completed/failed delivery counts bucketed by the requested granularity. */
    TrendSeries<DeliveryPerformancePoint> getPerformanceTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo);
}
