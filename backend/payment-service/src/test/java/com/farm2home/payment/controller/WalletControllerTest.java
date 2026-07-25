package com.farm2home.payment.controller;

import com.farm2home.payment.config.GatewayHeaderAuthFilter;
import com.farm2home.payment.config.SecurityConfig;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import com.farm2home.payment.service.WalletService;
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

@WebMvcTest(WalletController.class)
@Import(SecurityConfig.class)
class WalletControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private WalletService walletService;

    private final UUID customerId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(UUID userId) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210", Set.of("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/me → 200 with balance for the calling principal")
    void getMyWallet_ok() throws Exception {
        when(walletService.getWallet(customerId)).thenReturn(
                WalletResponse.builder().customerId(customerId).balance(new BigDecimal("500.00")).build());

        mockMvc.perform(get("/api/v1/wallets/me").with(authentication(authFor(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(500.00));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/topup → 200 with updated balance")
    void topUp_ok() throws Exception {
        TopUpWalletRequest req = new TopUpWalletRequest();
        req.setAmount(new BigDecimal("100.00"));
        when(walletService.topUp(eq(customerId), any())).thenReturn(
                WalletResponse.builder().customerId(customerId).balance(new BigDecimal("600.00")).build());

        mockMvc.perform(post("/api/v1/wallets/topup")
                        .with(authentication(authFor(customerId)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(600.00));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/transactions → 200 with page of transactions")
    void getTransactions_ok() throws Exception {
        when(walletService.getTransactions(eq(customerId), any())).thenReturn(
                new PageImpl<>(List.of(WalletTransactionResponse.builder().transactionType("CREDIT").build()),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/wallets/transactions").with(authentication(authFor(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transactionType").value("CREDIT"));
    }
}
