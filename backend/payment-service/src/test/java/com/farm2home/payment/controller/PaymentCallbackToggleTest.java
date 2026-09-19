package com.farm2home.payment.controller;

import com.farm2home.payment.config.GatewayHeaderAuthFilter;
import com.farm2home.payment.config.SecurityConfig;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The legacy /callback endpoint only when explicitly enabled (local dev / test). */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "farm2home.payment.legacy-callback-enabled=true")
class PaymentCallbackToggleTest {

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

    private UsernamePasswordAuthenticationToken auth(String role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of(role));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private PaymentCallbackRequest req() {
        PaymentCallbackRequest r = new PaymentCallbackRequest();
        r.setPaymentReference("PAY-TEST-1234");
        r.setSuccess(true);
        return r;
    }

    @Test
    @DisplayName("enabled + admin token → 200")
    void enabled_admin_ok() throws Exception {
        when(paymentService.processCallback(any())).thenReturn(
                PaymentResponse.builder().id(UUID.randomUUID()).paymentStatus("SUCCESS")
                        .amount(new BigDecimal("150.00")).build());

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(authentication(auth("SUPER_ADMIN")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("enabled + non-admin token → 403")
    void enabled_customer_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(authentication(auth("CUSTOMER")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req())))
                .andExpect(status().isForbidden());
    }
}
