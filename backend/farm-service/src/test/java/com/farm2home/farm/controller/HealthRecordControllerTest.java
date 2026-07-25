package com.farm2home.farm.controller;

import com.farm2home.farm.config.GatewayHeaderAuthFilter;
import com.farm2home.farm.config.SecurityConfig;
import com.farm2home.farm.config.UserPrincipal;
import com.farm2home.farm.domain.enums.HealthCondition;
import com.farm2home.farm.dto.request.CreateHealthRecordRequest;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.service.impl.HealthRecordServiceImpl;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthRecordController.class)
@Import(SecurityConfig.class)
class HealthRecordControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HealthRecordServiceImpl healthRecordService;

    private final UUID cowId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Test
    @DisplayName("POST /api/v1/farm/cows/{cowId}/health - admin role → 201")
    void create_admin_created() throws Exception {
        CreateHealthRecordRequest req = new CreateHealthRecordRequest();
        req.setRecordDate(LocalDate.now());
        req.setCondition(HealthCondition.HEALTHY);
        when(healthRecordService.create(eq(cowId), any())).thenReturn(
                HealthRecordResponse.builder().id(recordId).build());

        mockMvc.perform(post("/api/v1/farm/cows/{cowId}/health", cowId)
                        .with(authentication(authFor(true)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /api/v1/farm/cows/{cowId}/health/latest → 200")
    void latest_ok() throws Exception {
        when(healthRecordService.findLatest(cowId)).thenReturn(HealthRecordResponse.builder().id(recordId).build());

        mockMvc.perform(get("/api/v1/farm/cows/{cowId}/health/latest", cowId).with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/farm/cows/{cowId}/health/{id} - non-admin → 403")
    void delete_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/farm/cows/{cowId}/health/{id}", cowId, recordId)
                        .with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }
}
