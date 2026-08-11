package com.farm2home.invoice.controller;

import com.farm2home.invoice.config.GatewayHeaderAuthFilter;
import com.farm2home.invoice.config.SecurityConfig;
import com.farm2home.invoice.config.UserPrincipal;
import com.farm2home.invoice.dto.response.InvoiceResponse;
import com.farm2home.invoice.dto.response.InvoiceSummaryResponse;
import com.farm2home.invoice.service.impl.InvoiceServiceImpl;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
class InvoiceControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private InvoiceServiceImpl invoiceService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    // invoice-service's GatewayHeaderAuthFilter prepends "ROLE_" (mirrors farm-service's
    // convention, not the unprefixed one some other services use) - hasAnyRole(...) in
    // InvoiceController is written to match that.
    private UsernamePasswordAuthenticationToken manager() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("FARM_MANAGER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER")));
    }

    private UsernamePasswordAuthenticationToken customer() {
        UserPrincipal principal = new UserPrincipal(customerId, "9876543210", Set.of("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private InvoiceResponse buildResponse() {
        return InvoiceResponse.builder().id(invoiceId).invoiceNumber("INV-2026-000001")
                .orderId(orderId).customerId(customerId).subtotal(new BigDecimal("120.00"))
                .totalAmount(new BigDecimal("120.00")).build();
    }

    @Test
    @DisplayName("POST /invoices/generate/{orderId} → 201 for FARM_MANAGER")
    void generate_manager_ok() throws Exception {
        when(invoiceService.generate(orderId)).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/invoices/generate/{orderId}", orderId).with(authentication(manager())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-2026-000001"));
    }

    @Test
    @DisplayName("POST /invoices/generate/{orderId} → 403 for CUSTOMER")
    void generate_customer_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/invoices/generate/{orderId}", orderId).with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /invoices → 200 for FARM_MANAGER")
    void search_manager_ok() throws Exception {
        when(invoiceService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/invoices").with(authentication(manager())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /invoices → 403 for CUSTOMER (admin-only listing)")
    void search_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/invoices").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /invoices/me → 200 for CUSTOMER, scoped to caller")
    void findMine_customer_ok() throws Exception {
        when(invoiceService.findMine(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/invoices/me").with(authentication(customer())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /invoices/{id} → 200")
    void findById_ok() throws Exception {
        when(invoiceService.findById(eq(invoiceId), any())).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/invoices/{id}", invoiceId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(invoiceId.toString()));
    }

    @Test
    @DisplayName("GET /invoices/order/{orderId} → 200")
    void findByOrderId_ok() throws Exception {
        when(invoiceService.findByOrderId(eq(orderId), any())).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/invoices/order/{orderId}", orderId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }

    @Nested
    @DisplayName("GET /invoices/{id}/pdf")
    class DownloadPdf {

        @Test
        @DisplayName("returns a PDF byte stream with the correct content type")
        void downloadsPdf() throws Exception {
            byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46}; // "%PDF"
            when(invoiceService.generatePdf(eq(invoiceId), any())).thenReturn(pdfBytes);

            mockMvc.perform(get("/api/v1/invoices/{id}/pdf", invoiceId).with(authentication(customer())))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .content().contentType("application/pdf"));
        }
    }
}
