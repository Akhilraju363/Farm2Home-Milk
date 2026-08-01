package com.farm2home.farm.controller;

import com.farm2home.farm.config.GatewayHeaderAuthFilter;
import com.farm2home.farm.config.SecurityConfig;
import com.farm2home.farm.config.UserPrincipal;
import com.farm2home.farm.dto.request.CreateCowRequest;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.service.impl.CowServiceImpl;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CowController.class)
@Import(SecurityConfig.class)
class CowControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CowServiceImpl cowService;

    private final UUID cowId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Nested
    @DisplayName("POST /api/v1/farm/cows")
    class Create {

        @Test
        @DisplayName("admin role → 201")
        void admin_created() throws Exception {
            CreateCowRequest req = new CreateCowRequest();
            req.setTagNumber("TAG001");
            req.setBreed("Holstein");
            when(cowService.create(any())).thenReturn(CowResponse.builder().id(cowId).tagNumber("TAG001").build());

            mockMvc.perform(post("/api/v1/farm/cows")
                            .with(authentication(authFor(true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.tagNumber").value("TAG001"));
        }

        @Test
        @DisplayName("non-admin role → 403")
        void nonAdmin_forbidden() throws Exception {
            CreateCowRequest req = new CreateCowRequest();
            req.setTagNumber("TAG001");
            req.setBreed("Holstein");

            mockMvc.perform(post("/api/v1/farm/cows")
                            .with(authentication(authFor(false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("GET /api/v1/farm/cows?farmId= → 200")
    void findAll_byFarmId_ok() throws Exception {
        when(cowService.findAll(any(), any(), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(
                        List.of(CowResponse.builder().id(cowId).build()),
                        org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/farm/cows")
                        .param("farmId", UUID.randomUUID().toString())
                        .with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/farm/cows/ids?farmId= → 200")
    void findIdsByFarm_ok() throws Exception {
        UUID farmId = UUID.randomUUID();
        when(cowService.findIdsByFarm(farmId)).thenReturn(List.of(cowId));

        mockMvc.perform(get("/api/v1/farm/cows/ids")
                        .param("farmId", farmId.toString())
                        .with(authentication(authFor(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(cowId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/farm/cows/{id} → 200")
    void findById_ok() throws Exception {
        when(cowService.findById(cowId)).thenReturn(CowResponse.builder().id(cowId).build());

        mockMvc.perform(get("/api/v1/farm/cows/{id}", cowId).with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/farm/cows/{id}/status → admin → 200")
    void updateStatus_admin_ok() throws Exception {
        when(cowService.updateStatus(any(), any())).thenReturn(CowResponse.builder().id(cowId).status("SICK").build());

        mockMvc.perform(patch("/api/v1/farm/cows/{id}/status", cowId)
                        .with(authentication(authFor(true)))
                        .contentType("application/json")
                        .content("{\"status\":\"SICK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SICK"));
    }

    @Test
    @DisplayName("DELETE /api/v1/farm/cows/{id} → non-admin → 403")
    void delete_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/farm/cows/{id}", cowId).with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }
}
