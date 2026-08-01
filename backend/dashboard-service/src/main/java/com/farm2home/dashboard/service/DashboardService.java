package com.farm2home.dashboard.service;

import com.farm2home.dashboard.dto.response.DashboardSummaryResponse;

public interface DashboardService {

    /**
     * @param lowStockLimit             max low-stock products to embed (inventory-service caps/paginates)
     * @param recentNotificationsLimit  max recent notifications to embed (notification-service caps/paginates)
     */
    DashboardSummaryResponse getSummary(int lowStockLimit, int recentNotificationsLimit);
}
