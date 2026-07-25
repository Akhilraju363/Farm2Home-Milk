package com.farm2home.payment.controller;

import com.farm2home.payment.config.GatewayHeaderAuthFilter;
import com.farm2home.payment.config.SecurityConfig;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
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
    @DisplayName("POST /api/v1/payments/callback")
    class Callback {

        @Test
        @DisplayName("public endpoint → 200 without authentication")
        void noAuth_ok() throws Exception {
            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-1234");
            req.setSuccess(true);
            when(paymentService.processCallback(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/v1/payments/callback")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
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
}
