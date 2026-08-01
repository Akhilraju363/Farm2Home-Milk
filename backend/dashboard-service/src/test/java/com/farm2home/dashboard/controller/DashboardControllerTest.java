package com.farm2home.dashboard.controller;

import com.farm2home.dashboard.config.GatewayHeaderAuthFilter;
import com.farm2home.dashboard.config.SecurityConfig;
import com.farm2home.dashboard.dto.response.DashboardSummaryResponse;
import com.farm2home.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private DashboardService dashboardService;

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    private DashboardSummaryResponse buildResponse() {
        return DashboardSummaryResponse.builder()
                .totalCustomers(42)
                .activeSubscriptions(10)
                .todaysOrders(5)
                .pendingOrders(2)
                .completedDeliveriesToday(3)
                .revenueToday(BigDecimal.valueOf(1500))
                .revenueThisMonth(BigDecimal.valueOf(45000))
                .lowStockProductsCount(1)
                .lowStockProducts(List.of())
                .milkProductionToday(BigDecimal.valueOf(200))
                .recentNotifications(List.of())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary → 200 for SUPER_ADMIN")
    void getSummary_admin_ok() throws Exception {
        when(dashboardService.getSummary(anyInt(), anyInt())).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/dashboard/summary").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(42))
                .andExpect(jsonPath("$.data.activeSubscriptions").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary → 403 for non-manager CUSTOMER")
    void getSummary_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary honors custom limit query params")
    void getSummary_customLimits() throws Exception {
        when(dashboardService.getSummary(3, 5)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("lowStockLimit", "3")
                        .param("recentNotificationsLimit", "5")
                        .with(authentication(admin())))
                .andExpect(status().isOk());
    }
}
