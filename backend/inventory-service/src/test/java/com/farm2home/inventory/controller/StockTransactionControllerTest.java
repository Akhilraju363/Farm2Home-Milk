package com.farm2home.inventory.controller;

import com.farm2home.inventory.config.GatewayHeaderAuthFilter;
import com.farm2home.inventory.config.SecurityConfig;
import com.farm2home.inventory.domain.enums.TxnType;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockTransactionController.class)
@Import(SecurityConfig.class)
class StockTransactionControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private StockTransactionServiceImpl service;

    private final UUID itemId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authenticated() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @Test
    @DisplayName("POST .../transactions → 201")
    void transact_ok() throws Exception {
        StockTransactionRequest req = new StockTransactionRequest();
        req.setTxnType(TxnType.IN);
        req.setQuantity(new BigDecimal("25.00"));
        when(service.transact(eq(itemId), any())).thenReturn(
                StockTransactionResponse.builder().txnType("IN").quantity(new BigDecimal("25.00")).build());

        mockMvc.perform(post("/api/v1/inventory/{itemId}/transactions", itemId)
                        .with(authentication(authenticated()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.txnType").value("IN"));
    }

    @Test
    @DisplayName("GET .../transactions → 200 with page of history")
    void history_ok() throws Exception {
        when(service.findByItem(eq(itemId), any())).thenReturn(
                new PageImpl<>(List.of(StockTransactionResponse.builder().txnType("OUT").build()),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/inventory/{itemId}/transactions", itemId).with(authentication(authenticated())))
                .andExpect(status().isOk());
    }
}
