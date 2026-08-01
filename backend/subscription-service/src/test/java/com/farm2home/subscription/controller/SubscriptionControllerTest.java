package com.farm2home.subscription.controller;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.SubscriptionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.subscription.config.GatewayHeaderAuthFilter;
import com.farm2home.subscription.config.SecurityConfig;
import com.farm2home.subscription.config.UserPrincipal;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.service.SubscriptionService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SubscriptionService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID subId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(UUID userId, boolean admin) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private SubscriptionResponse buildResponse(String status) {
        return SubscriptionResponse.builder().id(subId).customerId(customerId).status(status).build();
    }

    // getSummary() is gated with hasAnyAuthority(...) (unprefixed role names), unlike the other
    // endpoints in this controller which rely on manual principal.isAdmin() checks - so the
    // granted authority here must match exactly (no "ROLE_" prefix, unlike authFor() above).
    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority(SecurityConstants.ROLE_FARM_MANAGER)));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @Nested
    @DisplayName("POST /api/v1/subscriptions")
    class Create {

        @Test
        @DisplayName("customer → uses own principal id regardless of body customerId")
        void customer_usesOwnId() throws Exception {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.5"))
                    .scheduleType(ScheduleType.DAILY).startDate(LocalDate.now()).build();
            when(service.create(any(), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(post("/api/v1/subscriptions")
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(service).create(any(), eq(customerId));
        }

        @Test
        @DisplayName("admin with explicit customerId → uses request's customerId")
        void admin_usesRequestCustomerId() throws Exception {
            UUID targetCustomer = UUID.randomUUID();
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .customerId(targetCustomer)
                    .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.5"))
                    .scheduleType(ScheduleType.DAILY).startDate(LocalDate.now()).build();
            when(service.create(any(), eq(targetCustomer))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(post("/api/v1/subscriptions")
                            .with(authentication(authFor(UUID.randomUUID(), true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(service).create(any(), eq(targetCustomer));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/{id}")
    class GetById {

        @Test
        @DisplayName("customer → filtered by own id")
        void customer_filtered() throws Exception {
            when(service.findById(eq(subId), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(get("/api/v1/subscriptions/{id}", subId).with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("admin → no customer filter (null)")
        void admin_noFilter() throws Exception {
            when(service.findById(eq(subId), isNull())).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(get("/api/v1/subscriptions/{id}", subId).with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/subscriptions/{id}")
    class Update {

        @Test
        @DisplayName("valid request → 200")
        void validRequest_ok() throws Exception {
            UpdateSubscriptionRequest req = UpdateSubscriptionRequest.builder()
                    .quantity(new BigDecimal("2.0")).build();
            when(service.update(eq(subId), any(), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(put("/api/v1/subscriptions/{id}", subId)
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/subscriptions/{id}")
    class Cancel {

        @Test
        @DisplayName("valid cancel → 200")
        void cancel_ok() throws Exception {
            mockMvc.perform(delete("/api/v1/subscriptions/{id}", subId).with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(service).cancel(subId, customerId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/subscriptions/{id}/pause and /resume")
    class PauseResume {

        @Test
        @DisplayName("pause → 200")
        void pause_ok() throws Exception {
            PauseSubscriptionRequest req = new PauseSubscriptionRequest(LocalDate.now().plusDays(7));
            when(service.pause(eq(subId), any(), eq(customerId))).thenReturn(buildResponse("PAUSED"));

            mockMvc.perform(post("/api/v1/subscriptions/{id}/pause", subId)
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAUSED"));
        }

        @Test
        @DisplayName("resume → 200")
        void resume_ok() throws Exception {
            when(service.resume(eq(subId), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(post("/api/v1/subscriptions/{id}/resume", subId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/summary")
    class GetSummary {

        @Test
        @DisplayName("FARM_MANAGER → 200")
        void admin_ok() throws Exception {
            when(service.getSummary()).thenReturn(
                    SubscriptionSummaryResponse.builder().activeSubscriptions(7L).build());

            mockMvc.perform(get("/api/v1/subscriptions/summary").with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activeSubscriptions").value(7));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/subscriptions/summary").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/reports")
    class GetReport {

        @Test
        @DisplayName("FARM_MANAGER → 200")
        void admin_ok() throws Exception {
            when(service.getReport(any(), any(), any(), any(), any(), any())).thenReturn(
                    ReportPage.<SubscriptionReportRow, SubscriptionReportSummary>builder()
                            .content(List.of())
                            .pageNumber(0).pageSize(20).totalElements(0).totalPages(0)
                            .summary(SubscriptionReportSummary.builder()
                                    .totalSubscriptions(0).activeSubscriptions(0).totalQuantity(BigDecimal.ZERO).build())
                            .build());

            mockMvc.perform(get("/api/v1/subscriptions/reports").with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.totalSubscriptions").value(0));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/subscriptions/reports").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/search")
    class Search {

        @Test
        @DisplayName("customer → filtered to own userId regardless of customerId param")
        void customer_filteredToOwnId() throws Exception {
            when(service.search(eq(customerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(List.of(buildResponse("ACTIVE"))),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/subscriptions/search")
                            .param("customerId", UUID.randomUUID().toString())
                            .param("keyword", "DAILY")
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(service).search(eq(customerId), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("admin with no customerId param → unfiltered (null)")
        void admin_noFilter() throws Exception {
            when(service.search(isNull(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(new java.util.ArrayList<>(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/v1/subscriptions/search").with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(status().isOk());

            verify(service).search(isNull(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/export")
    class Export {

        @Test
        @DisplayName("authenticated, CSV format → 200 with attachment headers")
        void authenticated_csv_ok() throws Exception {
            doNothing().when(service).export(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/subscriptions/export")
                            .param("format", "CSV")
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv"))
                    .andExpect(header().exists("Content-Disposition"));
        }

        @Test
        @DisplayName("customer → filtered to own userId regardless of customerId param")
        void customer_filteredToOwnId() throws Exception {
            doNothing().when(service).export(any(), any(), eq(customerId), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/subscriptions/export")
                            .param("format", "CSV")
                            .param("customerId", UUID.randomUUID().toString())
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk());

            verify(service).export(any(), any(), eq(customerId), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("admin with no customerId param → unfiltered (null)")
        void admin_noFilter() throws Exception {
            doNothing().when(service).export(any(), any(), isNull(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/subscriptions/export")
                            .param("format", "EXCEL")
                            .with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

            verify(service).export(any(), any(), isNull(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/analytics/subscription-trend")
    class GetSubscriptionTrend {

        @Test
        @DisplayName("FARM_MANAGER → 200")
        void authorized_ok() throws Exception {
            when(service.getSubscriptionTrend(eq(Granularity.DAILY), any(), any())).thenReturn(
                    TrendSeries.<SubscriptionTrendPoint>builder().granularity(Granularity.DAILY).points(List.of()).build());

            mockMvc.perform(get("/api/v1/subscriptions/analytics/subscription-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(admin())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/subscriptions/analytics/subscription-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }
}
