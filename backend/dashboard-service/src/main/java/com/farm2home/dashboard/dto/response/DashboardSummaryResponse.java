package com.farm2home.dashboard.dto.response;

import com.farm2home.common.core.dashboard.LowStockItemSummary;
import com.farm2home.common.core.dashboard.NotificationSummaryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The single response the frontend needs for the dashboard landing page - one call instead of
 * one per owning service. Each field degrades independently: if one downstream service times out
 * or errors, DashboardServiceImpl#withFallback substitutes a safe fallback value for that field
 * (e.g. a zero count or an empty list) rather than failing the whole response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    @Schema(description = "Total registered customers, platform-wide", example = "1240")
    private long totalCustomers;

    @Schema(description = "Subscriptions currently in ACTIVE status", example = "356")
    private long activeSubscriptions;

    @Schema(description = "Orders placed today (all statuses)", example = "48")
    private long todaysOrders;

    @Schema(description = "Orders currently awaiting fulfillment, across all dates", example = "12")
    private long pendingOrders;

    @Schema(description = "Deliveries marked completed today", example = "40")
    private long completedDeliveriesToday;

    @Schema(description = "Revenue recognized today, in rupees", example = "18500.00")
    private BigDecimal revenueToday;

    @Schema(description = "Revenue recognized so far this calendar month, in rupees", example = "412300.00")
    private BigDecimal revenueThisMonth;

    @Schema(description = "Total count of products currently below their low-stock threshold "
            + "(not limited by lowStockLimit)", example = "3")
    private long lowStockProductsCount;

    @Schema(description = "Up to `lowStockLimit` of the low-stock products, for display")
    private List<LowStockItemSummary> lowStockProducts;

    @Schema(description = "Total milk production recorded today, in liters", example = "1250.500")
    private BigDecimal milkProductionToday;

    @Schema(description = "Up to `recentNotificationsLimit` most recent notifications, newest first")
    private List<NotificationSummaryItem> recentNotifications;
}
