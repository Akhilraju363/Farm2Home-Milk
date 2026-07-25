package com.farm2home.production.controller;

import com.farm2home.production.config.GatewayHeaderAuthFilter;
import com.farm2home.production.config.SecurityConfig;
import com.farm2home.production.domain.enums.MilkSession;
import com.farm2home.production.dto.request.CreateMilkProductionRequest;
import com.farm2home.production.dto.response.MilkProductionResponse;
import com.farm2home.production.service.impl.MilkProductionServiceImpl;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MilkProductionController.class)
@Import(SecurityConfig.class)
class MilkProductionControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private MilkProductionServiceImpl service;

    private final UUID recordId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken("9876543210", null, authorities);
    }

    @Test
    @DisplayName("POST /api/v1/productions → 201")
    void create_ok() throws Exception {
        CreateMilkProductionRequest req = new CreateMilkProductionRequest();
        req.setCowId(UUID.randomUUID());
        req.setCollectionDate(LocalDate.now());
        req.setSession(MilkSession.MORNING);
        req.setQuantityLiters(new BigDecimal("10.50"));
        when(service.create(any())).thenReturn(MilkProductionResponse.builder().id(recordId).build());

        mockMvc.perform(post("/api/v1/productions")
                        .with(authentication(authFor(false)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /api/v1/productions/summary → 200")
    void summary_ok() throws Exception {
        when(service.getDailySummary(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/productions/summary")
                        .with(authentication(authFor(false)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/productions → 200")
    void findAll_ok() throws Exception {
        when(service.findAll(any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(MilkProductionResponse.builder().id(recordId).build()),
                        org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/productions").with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/productions/{id} → 200")
    void findById_ok() throws Exception {
        when(service.findById(recordId)).thenReturn(MilkProductionResponse.builder().id(recordId).build());

        mockMvc.perform(get("/api/v1/productions/{id}", recordId).with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/productions/cow/{cowId} → 200")
    void findByCow_ok() throws Exception {
        UUID cowId = UUID.randomUUID();
        when(service.findByCow(eq(cowId), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(MilkProductionResponse.builder().id(recordId).build()),
                        org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/productions/cow/{cowId}", cowId).with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/productions/cow/{cowId}/summary → 200")
    void cowSummary_ok() throws Exception {
        UUID cowId = UUID.randomUUID();
        when(service.getDailySummaryByCow(eq(cowId), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/productions/cow/{cowId}/summary", cowId)
                        .with(authentication(authFor(false)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/productions/{id} → 200")
    void update_ok() throws Exception {
        when(service.update(any(), any())).thenReturn(MilkProductionResponse.builder().id(recordId).build());

        mockMvc.perform(put("/api/v1/productions/{id}", recordId)
                        .with(authentication(authFor(false)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/productions/{id} - admin authority → 200")
    void delete_admin_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/productions/{id}", recordId).with(authentication(authFor(true))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/productions/{id} - non-admin authority → 403")
    void delete_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/productions/{id}", recordId).with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }
}
