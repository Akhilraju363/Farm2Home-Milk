package com.farm2home.reports.controller;

import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.reports.config.GatewayHeaderAuthFilter;
import com.farm2home.reports.config.SecurityConfig;
import com.farm2home.reports.service.ReportService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private ReportService reportService;

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @Test
    @DisplayName("GET /api/v1/reports/sales → 200 for SUPER_ADMIN")
    void getSalesReport_admin_ok() throws Exception {
        when(reportService.getSalesReport(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                ReportPage.<SalesReportRow, SalesReportSummary>builder()
                        .content(List.of())
                        .summary(SalesReportSummary.builder().totalOrders(0).totalRevenue(BigDecimal.ZERO).build())
                        .build());

        mockMvc.perform(get("/api/v1/reports/sales").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalOrders").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/reports/sales → 403 for CUSTOMER")
    void getSalesReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/sales").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/payments → 200 for SUPER_ADMIN")
    void getPaymentReport_admin_ok() throws Exception {
        when(reportService.getPaymentReport(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                ReportPage.<PaymentReportRow, PaymentReportSummary>builder()
                        .content(List.of())
                        .summary(PaymentReportSummary.builder()
                                .totalPayments(0).totalAmount(BigDecimal.ZERO).successAmount(BigDecimal.ZERO).build())
                        .build());

        mockMvc.perform(get("/api/v1/reports/payments").with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/reports/payments → 403 for CUSTOMER (class-level @PreAuthorize applies to every endpoint)")
    void getPaymentReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/payments").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/customers → 403 for CUSTOMER")
    void getCustomerReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/customers").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/subscriptions → 403 for CUSTOMER")
    void getSubscriptionReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/subscriptions").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/inventory → 403 for CUSTOMER")
    void getInventoryReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/production → 403 for CUSTOMER")
    void getProductionReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/production").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/deliveries → 403 for CUSTOMER")
    void getDeliveryReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/deliveries").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/sales/export → 200 for SUPER_ADMIN with attachment headers")
    void exportSalesReport_admin_ok() throws Exception {
        doNothing().when(reportService).exportSalesReport(any(), any(), any(), any(), any(), any(), any());

        MvcResult started = mockMvc.perform(get("/api/v1/reports/sales/export")
                        .param("format", "CSV")
                        .with(authentication(admin())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("GET /api/v1/reports/sales/export → 403 for CUSTOMER")
    void exportSalesReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/sales/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/customers/export → 403 for CUSTOMER")
    void exportCustomerReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/customers/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/subscriptions/export → 403 for CUSTOMER")
    void exportSubscriptionReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/subscriptions/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/inventory/export → 403 for CUSTOMER")
    void exportInventoryReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/production/export → 403 for CUSTOMER")
    void exportProductionReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/production/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/deliveries/export → 403 for CUSTOMER")
    void exportDeliveryReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/deliveries/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/reports/payments/export → 200 for SUPER_ADMIN with attachment headers")
    void exportPaymentReport_admin_ok() throws Exception {
        doNothing().when(reportService).exportPaymentReport(any(), any(), any(), any(), any(), any());

        MvcResult started = mockMvc.perform(get("/api/v1/reports/payments/export")
                        .param("format", "EXCEL")
                        .with(authentication(admin())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("GET /api/v1/reports/payments/export → 403 for CUSTOMER")
    void exportPaymentReport_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/payments/export").param("format", "CSV").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }
}
