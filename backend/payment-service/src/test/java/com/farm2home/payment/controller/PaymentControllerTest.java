package com.farm2home.payment.controller;

import com.farm2home.payment.config.GatewayHeaderAuthFilter;
import com.farm2home.payment.config.SecurityConfig;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.PaymentAnalyticsPoint;
import com.farm2home.common.core.analytics.RevenueTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.VerifyPaymentRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.service.PaymentService;
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
import java.util.List;
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

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PaymentService paymentService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(UUID userId, boolean admin) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210",
                admin ? java.util.Set.of("FARM_MANAGER") : java.util.Set.of("CUSTOMER"));
        // Bare authority names (no ROLE_ prefix) - every @PreAuthorize check in this controller
        // uses hasAnyAuthority(...), which (unlike hasAnyRole(...)) does not add the prefix
        // itself; must match GatewayHeaderAuthFilter's real granting behavior.
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private PaymentResponse buildResponse() {
        return PaymentResponse.builder().id(paymentId).paymentStatus("PENDING")
                .amount(new BigDecimal("150.00")).build();
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    class Initiate {

        @Test
        @DisplayName("valid request → 201")
        void validRequest_created() throws Exception {
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(UUID.randomUUID());
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);
            when(paymentService.initiate(any(), eq(customerId))).thenReturn(buildResponse());

            mockMvc.perform(post("/api/v1/payments")
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/callback (legacy, disabled by default)")
    class Callback {

        @Test
        @DisplayName("disabled by default → 404, service never called")
        void disabledByDefault_notFound() throws Exception {
            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-1234");
            req.setSuccess(true);

            mockMvc.perform(post("/api/v1/payments/callback")
                            .with(authentication(authFor(customerId, true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());

            verify(paymentService, org.mockito.Mockito.never()).processCallback(any());
        }

        @Test
        @DisplayName("no authentication → rejected (no longer a public endpoint)")
        void noAuth_rejected() throws Exception {
            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-1234");
            req.setSuccess(true);

            mockMvc.perform(post("/api/v1/payments/callback")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is4xxClientError())
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(s).isIn(401, 403);
                    });
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/{id}/verify")
    class Verify {

        @Test
        @DisplayName("valid request → 200")
        void validRequest_ok() throws Exception {
            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setGatewayOrderId("gw_order_1");
            req.setGatewayPaymentId("gw_pay_1");
            req.setSignature("sig");
            when(paymentService.verify(eq(paymentId), any(), eq(customerId), eq(false)))
                    .thenReturn(buildResponse());

            mockMvc.perform(post("/api/v1/payments/{id}/verify", paymentId)
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("unauthenticated → 403 (no bearer token — same as every other non-public endpoint)")
        void unauthenticated_forbidden() throws Exception {
            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setGatewayOrderId("gw_order_1");
            req.setGatewayPaymentId("gw_pay_1");
            req.setSignature("sig");

            mockMvc.perform(post("/api/v1/payments/{id}/verify", paymentId)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/webhook")
    class Webhook {

        @Test
        @DisplayName("public endpoint → 200 without authentication")
        void noAuth_ok() throws Exception {
            String payload = "{\"event\":\"payment.captured\"}";

            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("X-Razorpay-Signature", "sig")
                            .contentType("application/json")
                            .content(payload))
                    .andExpect(status().isOk());

            verify(paymentService).handleWebhook(payload, "sig");
        }

        @Test
        @DisplayName("missing signature header → still reaches the service (which rejects it)")
        void missingSignature_stillReachesService() throws Exception {
            String payload = "{\"event\":\"payment.captured\"}";

            mockMvc.perform(post("/api/v1/payments/webhook")
                            .contentType("application/json")
                            .content(payload))
                    .andExpect(status().isOk());

            verify(paymentService).handleWebhook(eq(payload), eq((String) null));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments")
    class FindAll {

        @Test
        @DisplayName("customer → passes own id and isAdmin=false")
        void customer_ownIdNotAdmin() throws Exception {
            when(paymentService.findAll(eq(customerId), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/payments").with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(paymentService).findAll(eq(customerId), eq(false), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/{id}")
    class FindById {

        @Test
        @DisplayName("existing payment → 200")
        void found() throws Exception {
            when(paymentService.findById(eq(paymentId), eq(customerId), eq(false))).thenReturn(buildResponse());

            mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(paymentId.toString()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/order/{orderId}")
    class FindByOrder {

        @Test
        @DisplayName("returns list")
        void returnsList() throws Exception {
            UUID orderId = UUID.randomUUID();
            when(paymentService.findByOrderId(eq(orderId), eq(customerId), eq(false)))
                    .thenReturn(List.of(buildResponse()));

            mockMvc.perform(get("/api/v1/payments/order/{orderId}", orderId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(paymentId.toString()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/order/{orderId}/payment-exists")
    class HasPayableProgress {

        @Test
        @DisplayName("any authenticated caller → 200, not scoped to the order's own customer")
        void anyAuthenticatedCaller_ok() throws Exception {
            UUID orderId = UUID.randomUUID();
            when(paymentService.hasPayableProgress(orderId)).thenReturn(true);

            mockMvc.perform(get("/api/v1/payments/order/{orderId}/payment-exists", orderId)
                            .with(authentication(authFor(UUID.randomUUID(), false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/{id}/refund")
    class Refund {

        @Test
        @DisplayName("admin role → 200")
        void admin_allowed() throws Exception {
            UUID adminId = UUID.randomUUID();
            when(paymentService.refund(eq(paymentId), eq(adminId), eq(true))).thenReturn(buildResponse());

            mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                            .with(authentication(authFor(adminId, true))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("non-admin role → 403")
        void nonAdmin_forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/summary")
    class Summary {

        // The summary endpoint uses hasAnyAuthority(...) (matched against raw role strings,
        // no ROLE_ prefix) rather than hasAnyRole(...) like the rest of this controller, so
        // it needs its own authority builder distinct from authFor() above.
        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                    java.util.Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(paymentService.getSummary()).thenReturn(
                    PaymentSummaryResponse.builder()
                            .revenueToday(new BigDecimal("500.00"))
                            .revenueThisMonth(new BigDecimal("12000.00"))
                            .build());

            mockMvc.perform(get("/api/v1/payments/summary")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.revenueToday").value(500.00));
        }

        @Test
        @DisplayName("customer authority → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/payments/summary")
                            .with(authentication(authorityFor("CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/reports")
    class GetReport {

        // Same rationale as Summary above: this endpoint uses hasAnyAuthority(...) rather than
        // hasAnyRole(...), so it needs its own authority builder distinct from authFor().
        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                    java.util.Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(paymentService.getReport(any(), any(), any(), any(), any())).thenReturn(
                    com.farm2home.common.core.reports.ReportPage.<com.farm2home.common.core.reports.PaymentReportRow,
                            com.farm2home.common.core.reports.PaymentReportSummary>builder()
                            .content(List.of())
                            .pageNumber(0).pageSize(20).totalElements(0).totalPages(0)
                            .summary(com.farm2home.common.core.reports.PaymentReportSummary.builder()
                                    .totalPayments(0).totalAmount(BigDecimal.ZERO).successAmount(BigDecimal.ZERO).build())
                            .build());

            mockMvc.perform(get("/api/v1/payments/reports")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.totalPayments").value(0));
        }

        @Test
        @DisplayName("customer authority → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/payments/reports")
                            .with(authentication(authorityFor("CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/export")
    class Export {

        // Same rationale as Summary/GetReport above: this endpoint uses hasAnyAuthority(...)
        // rather than hasAnyRole(...), so it needs its own authority builder distinct from authFor().
        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                    java.util.Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority, CSV format → 200 with attachment headers")
        void farmManager_csv_ok() throws Exception {
            doNothing().when(paymentService).export(any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/payments/export")
                            .param("format", "CSV")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv"))
                    .andExpect(header().exists("Content-Disposition"));
        }

        @Test
        @DisplayName("customer authority → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/payments/export").param("format", "CSV")
                            .with(authentication(authorityFor("CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/analytics/revenue-trend")
    class GetRevenueTrend {

        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                    java.util.Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(paymentService.getRevenueTrend(eq(Granularity.DAILY), any(), any())).thenReturn(
                    TrendSeries.<RevenueTrendPoint>builder()
                            .granularity(Granularity.DAILY)
                            .points(List.of(RevenueTrendPoint.builder()
                                    .period(java.time.LocalDate.of(2026, 1, 1))
                                    .revenue(new BigDecimal("500.00")).build()))
                            .build());

            mockMvc.perform(get("/api/v1/payments/analytics/revenue-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points[0].revenue").value(500.00));
        }

        @Test
        @DisplayName("customer authority → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/payments/analytics/revenue-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(authorityFor("CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/analytics/payment-trend")
    class GetPaymentAnalytics {

        private UsernamePasswordAuthenticationToken authorityFor(String authority) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                    java.util.Set.of(authority));
            return new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(authority)));
        }

        @Test
        @DisplayName("farm manager authority → 200")
        void farmManager_ok() throws Exception {
            when(paymentService.getPaymentAnalytics(eq(Granularity.MONTHLY), any(), any())).thenReturn(
                    TrendSeries.<PaymentAnalyticsPoint>builder()
                            .granularity(Granularity.MONTHLY)
                            .points(List.of(PaymentAnalyticsPoint.builder()
                                    .period(java.time.LocalDate.of(2026, 1, 1))
                                    .totalPayments(4).totalAmount(new BigDecimal("350.00"))
                                    .successCount(3).failedCount(1).build()))
                            .build());

            mockMvc.perform(get("/api/v1/payments/analytics/payment-trend")
                            .param("granularity", "MONTHLY")
                            .with(authentication(authorityFor("FARM_MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points[0].totalPayments").value(4));
        }

        @Test
        @DisplayName("customer authority → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/payments/analytics/payment-trend")
                            .param("granularity", "MONTHLY")
                            .with(authentication(authorityFor("CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }
}
