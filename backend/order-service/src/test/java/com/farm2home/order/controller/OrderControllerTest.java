package com.farm2home.order.controller;

import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.order.config.GatewayHeaderAuthFilter;
import com.farm2home.order.config.SecurityConfig;
import com.farm2home.order.config.UserPrincipal;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.dto.request.CreateOrderItemRequest;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.dto.response.OrderResponse;
import com.farm2home.order.service.DailyOrderGenerationService;
import com.farm2home.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// addFilters stays at its default (true): SecurityMockMvcRequestPostProcessors.authentication()
// relies on Spring Security's own test filter chain to install the principal into the
// SecurityContext - addFilters=false would strip that filter chain out too and leave
// @AuthenticationPrincipal unresolved (null). The real SecurityConfig is imported (rather than
// relying on Boot's stateful-app security defaults) so CSRF-disabled/stateless/@PreAuthorize
// behavior matches production exactly.
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private OrderService orderService;
    @MockBean private DailyOrderGenerationService generationService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    // GatewayHeaderAuthFilter grants unprefixed authorities (matching the other services'
    // convention), so this mirrors that - no "ROLE_" prefix.
    private UsernamePasswordAuthenticationToken authFor(UUID userId, boolean admin) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private OrderResponse buildResponse() {
        return OrderResponse.builder().id(orderId).orderNumber("ORD-2026-000001")
                .status(OrderStatus.PENDING.name()).totalAmount(new BigDecimal("80.00")).build();
    }

    // getSummary()/getReports() are gated with hasAnyAuthority(...) (unprefixed role names) -
    // separate from authFor() since those tests don't need a UserPrincipal.
    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority(SecurityConstants.ROLE_FARM_MANAGER)));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class Create {

        @Test
        @DisplayName("customer creates order → uses own principal userId as customerId")
        void customer_usesOwnId() throws Exception {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of(CreateOrderItemRequest.builder()
                            .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.0")).build()))
                    .build();
            when(orderService.createManualOrder(any(), eq(customerId))).thenReturn(buildResponse());

            mockMvc.perform(post("/api/v1/orders")
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.orderNumber").value("ORD-2026-000001"));

            verify(orderService).createManualOrder(any(), eq(customerId));
        }

        @Test
        @DisplayName("admin creates order with explicit customerId → uses request's customerId")
        void admin_usesRequestCustomerId() throws Exception {
            UUID targetCustomer = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .customerId(targetCustomer)
                    .orderDate(LocalDate.now())
                    .items(List.of(CreateOrderItemRequest.builder()
                            .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.0")).build()))
                    .build();
            when(orderService.createManualOrder(any(), eq(targetCustomer))).thenReturn(buildResponse());

            mockMvc.perform(post("/api/v1/orders")
                            .with(authentication(authFor(adminId, true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(orderService).createManualOrder(any(), eq(targetCustomer));
        }

        @Test
        @DisplayName("missing items → 400 validation error")
        void missingItems_badRequest() throws Exception {
            CreateOrderRequest req = CreateOrderRequest.builder().orderDate(LocalDate.now()).build();

            mockMvc.perform(post("/api/v1/orders")
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders")
    class GetAll {

        @Test
        @DisplayName("customer → filtered to own userId regardless of customerId param")
        void customer_filteredToOwnId() throws Exception {
            // Explicit PageRequest (not the unpaged() default) - PageImpl's Jackson
            // serialization is unreliable with Pageable.unpaged().
            when(orderService.findAll(eq(customerId), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(List.of(buildResponse())),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/orders")
                            .with(authentication(authFor(customerId, false)))
                            .param("customerId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk());

            verify(orderService).findAll(eq(customerId), any());
        }

        @Test
        @DisplayName("admin with no customerId param → unfiltered (null)")
        void admin_noFilter() throws Exception {
            when(orderService.findAll(eq(null), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/v1/orders").with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(status().isOk());

            verify(orderService).findAll(eq(null), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id}")
    class GetById {

        @Test
        @DisplayName("existing order → 200")
        void found() throws Exception {
            when(orderService.findById(eq(orderId), eq(customerId))).thenReturn(buildResponse());

            mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(orderId.toString()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/subscription/{subscriptionId}")
    class GetBySubscription {

        private final UUID subscriptionId = UUID.randomUUID();

        @Test
        @DisplayName("customer → filtered to own userId, not just the subscriptionId")
        void customer_filteredToOwnId() throws Exception {
            when(orderService.findBySubscription(eq(subscriptionId), eq(customerId), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(List.of(buildResponse())),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/orders/subscription/{subscriptionId}", subscriptionId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(orderService).findBySubscription(eq(subscriptionId), eq(customerId), any());
        }

        @Test
        @DisplayName("admin → unfiltered (null customerId)")
        void admin_noFilter() throws Exception {
            when(orderService.findBySubscription(eq(subscriptionId), eq(null), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/v1/orders/subscription/{subscriptionId}", subscriptionId)
                            .with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(status().isOk());

            verify(orderService).findBySubscription(eq(subscriptionId), eq(null), any());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/orders/{id}/status")
    class UpdateStatus {

        @Test
        @DisplayName("valid transition → 200, passes actor id through")
        void validTransition_ok() throws Exception {
            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.ASSIGNED);
            when(orderService.updateStatus(eq(orderId), any(), eq(customerId), eq(false), eq(customerId)))
                    .thenReturn(buildResponse());

            mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            verify(orderService).updateStatus(eq(orderId), any(), eq(customerId), eq(false), eq(customerId));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/orders/{id}")
    class Cancel {

        @Test
        @DisplayName("customer cancels own order → 200")
        void cancel_ok() throws Exception {
            mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(orderService).cancel(orderId, customerId, false, customerId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/orders/generate")
    class GenerateOrders {

        @Test
        @DisplayName("with explicit date → delegates with that date")
        void explicitDate_delegates() throws Exception {
            LocalDate date = LocalDate.of(2026, 6, 24);
            when(generationService.generateOrdersForDate(date)).thenReturn(
                    GenerationResultResponse.builder().date(date).ordersCreated(5).skipped(1).build());

            mockMvc.perform(post("/api/v1/orders/generate")
                            .with(authentication(authFor(UUID.randomUUID(), true)))
                            .param("date", "2026-06-24"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ordersCreated").value(5));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/summary")
    class GetSummary {

        @Test
        @DisplayName("FARM_MANAGER → 200")
        void admin_ok() throws Exception {
            when(orderService.getSummary()).thenReturn(
                    OrderSummaryResponse.builder().todaysOrders(5L).pendingOrders(3L).build());

            mockMvc.perform(get("/api/v1/orders/summary").with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.todaysOrders").value(5))
                    .andExpect(jsonPath("$.data.pendingOrders").value(3));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/orders/summary").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/reports")
    class GetReport {

        @Test
        @DisplayName("FARM_MANAGER → 200")
        void admin_ok() throws Exception {
            when(orderService.getReport(any(), any(), any(), any(), any(), any())).thenReturn(
                    com.farm2home.common.core.reports.ReportPage.<com.farm2home.common.core.reports.SalesReportRow,
                            com.farm2home.common.core.reports.SalesReportSummary>builder()
                            .content(java.util.List.of())
                            .pageNumber(0).pageSize(20).totalElements(0).totalPages(0)
                            .summary(com.farm2home.common.core.reports.SalesReportSummary.builder()
                                    .totalOrders(0).totalRevenue(java.math.BigDecimal.ZERO).build())
                            .build());

            mockMvc.perform(get("/api/v1/orders/reports").with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.totalOrders").value(0));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/orders/reports").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/search")
    class Search {

        @Test
        @DisplayName("customer → filtered to own userId regardless of customerId param")
        void customer_filteredToOwnId() throws Exception {
            when(orderService.search(eq(customerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(List.of(buildResponse())),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/orders/search")
                            .param("customerId", UUID.randomUUID().toString())
                            .param("keyword", "ORD")
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(orderService).search(eq(customerId), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("admin with no customerId param → unfiltered (null)")
        void admin_noFilter() throws Exception {
            when(orderService.search(eq(null), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/v1/orders/search").with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(status().isOk());

            verify(orderService).search(eq(null), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/export")
    class Export {

        @Test
        @DisplayName("customer, CSV format → 200 with attachment headers, filtered to own userId")
        void customer_csv_ok() throws Exception {
            doNothing().when(orderService).export(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/orders/export")
                            .param("format", "CSV")
                            .param("customerId", UUID.randomUUID().toString())
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv"))
                    .andExpect(header().exists("Content-Disposition"));

            verify(orderService).export(eq(com.farm2home.common.export.ExportFormat.CSV), any(), eq(customerId),
                    any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("admin with no customerId param → unfiltered (null), Excel format → 200")
        void admin_noFilter_excel_ok() throws Exception {
            doNothing().when(orderService).export(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/orders/export")
                            .param("format", "EXCEL")
                            .with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Disposition"));

            verify(orderService).export(any(), any(), eq(null), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }
    }
}
