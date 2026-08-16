package com.farm2home.delivery.controller;

import com.farm2home.delivery.config.GatewayHeaderAuthFilter;
import com.farm2home.delivery.config.SecurityConfig;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.common.core.analytics.DeliveryPerformancePoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.service.DeliveryAssignmentService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryAssignmentController.class)
@Import(SecurityConfig.class)
class DeliveryAssignmentControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DeliveryAssignmentService assignmentService;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID partnerUserId = UUID.randomUUID();

    // GatewayHeaderAuthFilter grants unprefixed authorities (matching the other services'
    // convention), so this mirrors that - no "ROLE_" prefix.
    private UsernamePasswordAuthenticationToken authFor(UUID userId, boolean admin) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("DELIVERY_PARTNER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("DELIVERY_PARTNER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Nested
    @DisplayName("POST /api/v1/delivery/assignments")
    class ManualAssign {

        @Test
        @DisplayName("admin role → 201")
        void admin_created() throws Exception {
            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(UUID.randomUUID());
            req.setDeliveryPartnerId(UUID.randomUUID());
            req.setRouteId(UUID.randomUUID());
            when(assignmentService.manualAssign(any())).thenReturn(
                    AssignmentResponse.builder().id(assignmentId).build());

            mockMvc.perform(post("/api/v1/delivery/assignments")
                            .with(authentication(authFor(UUID.randomUUID(), true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("non-admin role → 403")
        void nonAdmin_forbidden() throws Exception {
            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(UUID.randomUUID());
            req.setDeliveryPartnerId(UUID.randomUUID());
            req.setRouteId(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/delivery/assignments")
                            .with(authentication(authFor(UUID.randomUUID(), false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments")
    class FindAll {

        @Test
        @DisplayName("delivery partner → passes own userId and isAdmin=false")
        void partner_ownIdNotAdmin() throws Exception {
            when(assignmentService.findAll(eq(partnerUserId), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.of(AssignmentResponse.builder().id(assignmentId).build()),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/delivery/assignments").with(authentication(authFor(partnerUserId, false))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("delivery manager → isAdmin=true (isAdmin() broadened to include DELIVERY_MANAGER)")
        void deliveryManager_isAdminTrue() throws Exception {
            UUID managerId = UUID.randomUUID();
            UserPrincipal principal = new UserPrincipal(managerId, "9876543210", Set.of("DELIVERY_MANAGER"));
            var auth = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_MANAGER")));
            when(assignmentService.findAll(eq(managerId), eq(true), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/v1/delivery/assignments").with(authentication(auth)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/{id}")
    class FindById {

        @Test
        @DisplayName("existing assignment → 200")
        void found() throws Exception {
            when(assignmentService.findById(eq(assignmentId), eq(partnerUserId), eq(false)))
                    .thenReturn(AssignmentResponse.builder().id(assignmentId).build());

            mockMvc.perform(get("/api/v1/delivery/assignments/{id}", assignmentId)
                            .with(authentication(authFor(partnerUserId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/delivery/assignments/{id}/status")
    class UpdateStatus {

        @Test
        @DisplayName("valid transition → 200")
        void validTransition_ok() throws Exception {
            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.DELIVERED);
            req.setFailureReason("n/a");
            req.setDeliveryProof("signed-by-customer");
            when(assignmentService.updateStatus(eq(assignmentId), any(), eq(partnerUserId), eq(false)))
                    .thenReturn(AssignmentResponse.builder().id(assignmentId).status("DELIVERED").build());

            mockMvc.perform(patch("/api/v1/delivery/assignments/{id}/status", assignmentId)
                            .with(authentication(authFor(partnerUserId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DELIVERED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/summary")
    class Summary {

        // Separate from authFor() since this test doesn't need a UserPrincipal, just a bare
        // authority to check against this endpoint's hasAnyAuthority(...) restriction.
        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(assignmentService.getSummary()).thenReturn(
                    DeliverySummaryResponse.builder().completedDeliveriesToday(5L).build());

            mockMvc.perform(get("/api/v1/delivery/assignments/summary")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.completedDeliveriesToday").value(5));
        }

        @Test
        @DisplayName("delivery partner authority → 403")
        void deliveryPartner_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/delivery/assignments/summary")
                            .with(authentication(authorityFor("DELIVERY_PARTNER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/reports")
    class GetReport {

        // Same rationale as Summary above - doesn't need a UserPrincipal, just a bare authority.
        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(assignmentService.getReport(any(), any(), any(), any(), any())).thenReturn(
                    com.farm2home.common.core.reports.ReportPage.<com.farm2home.common.core.reports.DeliveryReportRow,
                            com.farm2home.common.core.reports.DeliveryReportSummary>builder()
                            .content(java.util.List.of())
                            .pageNumber(0).pageSize(20).totalElements(0).totalPages(0)
                            .summary(com.farm2home.common.core.reports.DeliveryReportSummary.builder()
                                    .totalDeliveries(0).completedCount(0).failedCount(0).build())
                            .build());

            mockMvc.perform(get("/api/v1/delivery/assignments/reports")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.totalDeliveries").value(0));
        }

        @Test
        @DisplayName("delivery partner authority → 403")
        void deliveryPartner_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/delivery/assignments/reports")
                            .with(authentication(authorityFor("DELIVERY_PARTNER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/search")
    class Search {

        @Test
        @DisplayName("delivery partner → passes own userId and isAdmin=false")
        void partner_ownIdNotAdmin() throws Exception {
            when(assignmentService.search(eq(partnerUserId), eq(false), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(AssignmentResponse.builder().id(assignmentId).build()),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/delivery/assignments/search")
                            .param("keyword", "Ravi")
                            .with(authentication(authFor(partnerUserId, false))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("admin → passes own userId and isAdmin=true")
        void admin_isAdminTrue() throws Exception {
            UUID adminId = UUID.randomUUID();
            when(assignmentService.search(eq(adminId), eq(true), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/v1/delivery/assignments/search").with(authentication(authFor(adminId, true))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/analytics/performance-trend")
    class GetPerformanceTrend {

        // Same rationale as Summary/GetReport above: this endpoint uses hasAnyAuthority(...)
        // rather than hasAnyRole(...), so it needs its own authority builder distinct from authFor().
        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(assignmentService.getPerformanceTrend(eq(Granularity.DAILY), any(), any())).thenReturn(
                    TrendSeries.<DeliveryPerformancePoint>builder()
                            .granularity(Granularity.DAILY)
                            .points(List.of(DeliveryPerformancePoint.builder()
                                    .period(java.time.LocalDate.of(2026, 1, 1))
                                    .totalDeliveries(4).completedDeliveries(3).failedDeliveries(1).build()))
                            .build());

            mockMvc.perform(get("/api/v1/delivery/assignments/analytics/performance-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points[0].totalDeliveries").value(4));
        }

        @Test
        @DisplayName("delivery partner authority → 403")
        void deliveryPartner_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/delivery/assignments/analytics/performance-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(authorityFor("DELIVERY_PARTNER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/order/{orderId}")
    class FindByOrder {

        @Test
        @DisplayName("admin → passes own userId and isAdmin=true")
        void admin_isAdminTrue() throws Exception {
            UUID orderId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            when(assignmentService.findByOrderId(eq(orderId), eq(adminId), eq(true)))
                    .thenReturn(List.of(AssignmentResponse.builder().id(assignmentId).build()));

            mockMvc.perform(get("/api/v1/delivery/assignments/order/{orderId}", orderId)
                            .with(authentication(authFor(adminId, true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(assignmentId.toString()));
        }

        @Test
        @DisplayName("delivery partner → passes own userId and isAdmin=false")
        void partner_ownIdNotAdmin() throws Exception {
            UUID orderId = UUID.randomUUID();
            when(assignmentService.findByOrderId(eq(orderId), eq(partnerUserId), eq(false)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/delivery/assignments/order/{orderId}", orderId)
                            .with(authentication(authFor(partnerUserId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }
}
