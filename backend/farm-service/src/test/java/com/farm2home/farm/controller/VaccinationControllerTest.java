package com.farm2home.farm.controller;

import com.farm2home.farm.config.GatewayHeaderAuthFilter;
import com.farm2home.farm.config.SecurityConfig;
import com.farm2home.farm.config.UserPrincipal;
import com.farm2home.farm.dto.request.CreateVaccinationRequest;
import com.farm2home.farm.dto.response.VaccinationResponse;
import com.farm2home.farm.service.impl.VaccinationServiceImpl;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VaccinationController.class)
@Import(SecurityConfig.class)
class VaccinationControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private VaccinationServiceImpl vaccinationService;

    private final UUID cowId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Test
    @DisplayName("POST /api/v1/farm/cows/{cowId}/vaccinations - admin role → 201")
    void create_admin_created() throws Exception {
        CreateVaccinationRequest req = new CreateVaccinationRequest();
        req.setVaccineName("FMD");
        req.setAdministeredAt(LocalDate.now());
        when(vaccinationService.create(org.mockito.ArgumentMatchers.eq(cowId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(VaccinationResponse.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(post("/api/v1/farm/cows/{cowId}/vaccinations", cowId)
                        .with(authentication(authFor(true)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/v1/farm/cows/{cowId}/vaccinations - non-admin role → 403")
    void create_nonAdmin_forbidden() throws Exception {
        CreateVaccinationRequest req = new CreateVaccinationRequest();
        req.setVaccineName("FMD");
        req.setAdministeredAt(LocalDate.now());

        mockMvc.perform(post("/api/v1/farm/cows/{cowId}/vaccinations", cowId)
                        .with(authentication(authFor(false)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/farm/vaccinations/upcoming - non-admin role → 403")
    void upcoming_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/farm/vaccinations/upcoming").with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/farm/vaccinations/upcoming - admin role → 200")
    void upcoming_admin_ok() throws Exception {
        when(vaccinationService.findUpcoming(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/farm/vaccinations/upcoming").with(authentication(authFor(true))))
                .andExpect(status().isOk());
    }
}
