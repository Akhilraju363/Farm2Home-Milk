package com.farm2home.inventory.controller;

import com.farm2home.inventory.config.GatewayHeaderAuthFilter;
import com.farm2home.inventory.config.SecurityConfig;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.InventoryConsumptionPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.enums.UnitType;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.service.impl.InventoryItemServiceImpl;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// inventory-service's GatewayHeaderAuthFilter builds authorities from raw role strings
// (no ROLE_ prefix), matched here by hasAnyAuthority(...) in the controller - unlike most
// other services in this codebase, which prefix with ROLE_ and use hasAnyRole(...).
@WebMvcTest(InventoryItemController.class)
@Import(SecurityConfig.class)
class InventoryItemControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private InventoryItemServiceImpl service;
    @MockBean private StockTransactionServiceImpl txnService;

    private final UUID itemId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken("9876543210", null, authorities);
    }

    @Nested
    @DisplayName("POST /api/v1/inventory")
    class Create {

        @Test
        @DisplayName("admin authority → 201")
        void admin_created() throws Exception {
            CreateInventoryItemRequest req = new CreateInventoryItemRequest();
            req.setItemName("Rice Straw");
            req.setItemType(ItemType.FEED);
            req.setUnit(UnitType.KG);
            when(service.create(any())).thenReturn(
                    InventoryItemResponse.builder().id(itemId).itemName("Rice Straw").build());

            mockMvc.perform(post("/api/v1/inventory")
                            .with(authentication(authFor(true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.itemName").value("Rice Straw"));
        }

        @Test
        @DisplayName("non-admin authority → 403")
        void nonAdmin_forbidden() throws Exception {
            CreateInventoryItemRequest req = new CreateInventoryItemRequest();
            req.setItemName("Rice Straw");
            req.setItemType(ItemType.FEED);
            req.setUnit(UnitType.KG);

            mockMvc.perform(post("/api/v1/inventory")
                            .with(authentication(authFor(false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("GET /api/v1/inventory - any authenticated user → 200")
    void findAll_ok() throws Exception {
        when(service.findAll(any(), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(InventoryItemResponse.builder().id(itemId).build()),
                        org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/inventory").with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/{id} - any authenticated user → 200")
    void findById_ok() throws Exception {
        when(service.findById(itemId)).thenReturn(InventoryItemResponse.builder().id(itemId).build());

        mockMvc.perform(get("/api/v1/inventory/{id}", itemId).with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/{id} - admin authority → 200")
    void update_admin_ok() throws Exception {
        com.farm2home.inventory.dto.request.UpdateInventoryItemRequest req =
                new com.farm2home.inventory.dto.request.UpdateInventoryItemRequest();
        when(service.update(any(), any())).thenReturn(InventoryItemResponse.builder().id(itemId).build());

        mockMvc.perform(put("/api/v1/inventory/{id}", itemId)
                        .with(authentication(authFor(true)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/low-stock → 200")
    void lowStock_ok() throws Exception {
        when(service.findLowStock()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inventory/low-stock").with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/inventory/{id} - non-admin authority → 403")
    void delete_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/{id}", itemId).with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/summary - admin authority → 200")
    void summary_admin_ok() throws Exception {
        when(service.getSummary(anyInt())).thenReturn(
                com.farm2home.common.core.dashboard.InventorySummaryResponse.builder()
                        .lowStockCount(2L)
                        .topItems(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/inventory/summary").with(authentication(authFor(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockCount").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/summary - non-admin authority → 403")
    void summary_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/summary").with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }

    @Nested
    @DisplayName("GET /api/v1/inventory/search")
    class Search {

        @Test
        @DisplayName("authenticated but unprivileged user → 200")
        void nonAdmin_ok() throws Exception {
            when(service.search(any(), any(), any(), any(), any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(List.of(InventoryItemResponse.builder().id(itemId).build()),
                            org.springframework.data.domain.PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/inventory/search")
                            .param("keyword", "rice")
                            .with(authentication(authFor(false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(itemId.toString()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inventory/transactions/reports")
    class GetReport {

        @Test
        @DisplayName("admin authority → 200")
        void admin_ok() throws Exception {
            when(txnService.getReport(any(), any(), any(), any(), any())).thenReturn(
                    com.farm2home.common.core.reports.ReportPage.<com.farm2home.common.core.reports.InventoryReportRow,
                            com.farm2home.common.core.reports.InventoryReportSummary>builder()
                            .content(List.of())
                            .pageNumber(0).pageSize(20).totalElements(0).totalPages(0)
                            .summary(com.farm2home.common.core.reports.InventoryReportSummary.builder()
                                    .totalTransactions(0)
                                    .totalInQuantity(java.math.BigDecimal.ZERO)
                                    .totalOutQuantity(java.math.BigDecimal.ZERO)
                                    .build())
                            .build());

            mockMvc.perform(get("/api/v1/inventory/transactions/reports").with(authentication(authFor(true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.totalTransactions").value(0));
        }

        @Test
        @DisplayName("non-admin authority → 403")
        void nonAdmin_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/transactions/reports").with(authentication(authFor(false))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inventory/export")
    class Export {

        @Test
        @DisplayName("authenticated, CSV format → 200 with attachment headers")
        void authenticated_csv_ok() throws Exception {
            doNothing().when(service).export(any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/inventory/export")
                            .param("format", "CSV")
                            .with(authentication(authFor(false))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv"))
                    .andExpect(header().exists("Content-Disposition"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inventory/analytics/consumption-trend")
    class GetConsumptionTrend {

        @Test
        @DisplayName("admin authority → 200")
        void admin_ok() throws Exception {
            when(txnService.getConsumptionTrend(eq(Granularity.DAILY), any(), any())).thenReturn(
                    TrendSeries.<InventoryConsumptionPoint>builder()
                            .granularity(Granularity.DAILY)
                            .points(List.of(InventoryConsumptionPoint.builder()
                                    .period(java.time.LocalDate.of(2026, 1, 1))
                                    .consumedQuantity(new java.math.BigDecimal("40.00")).build()))
                            .build());

            mockMvc.perform(get("/api/v1/inventory/analytics/consumption-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(authFor(true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points[0].consumedQuantity").value(40.00));
        }

        @Test
        @DisplayName("non-admin authority → 403")
        void nonAdmin_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/analytics/consumption-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(authFor(false))))
                    .andExpect(status().isForbidden());
        }
    }
}
