package com.farm2home.delivery.controller;

import com.farm2home.delivery.config.GatewayHeaderAuthFilter;
import com.farm2home.delivery.config.SecurityConfig;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.service.impl.DeliveryPartnerServiceImpl;
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

@WebMvcTest(DeliveryPartnerController.class)
@Import(SecurityConfig.class)
class DeliveryPartnerControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DeliveryPartnerServiceImpl partnerService;

    private final UUID partnerId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("DELIVERY_PARTNER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_PARTNER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Test
    @DisplayName("POST /api/v1/delivery/partners - admin role → 201")
    void create_admin_created() throws Exception {
        CreatePartnerRequest req = new CreatePartnerRequest();
        req.setUserId(UUID.randomUUID());
        req.setName("Raj Kumar");
        req.setMobile("9876543210");
        when(partnerService.create(any())).thenReturn(PartnerResponse.builder().id(partnerId).name("Raj Kumar").build());

        mockMvc.perform(post("/api/v1/delivery/partners")
                        .with(authentication(authFor(true)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Raj Kumar"));
    }

    @Test
    @DisplayName("GET /api/v1/delivery/partners - non-admin role → 403")
    void findAll_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/partners").with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/delivery/partners/{id} - any authenticated user → 200")
    void findById_ok() throws Exception {
        when(partnerService.findById(partnerId)).thenReturn(PartnerResponse.builder().id(partnerId).build());

        mockMvc.perform(get("/api/v1/delivery/partners/{id}", partnerId).with(authentication(authFor(false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/delivery/partners/{id} - admin role → 200")
    void update_admin_ok() throws Exception {
        when(partnerService.update(any(), any())).thenReturn(PartnerResponse.builder().id(partnerId).build());

        mockMvc.perform(put("/api/v1/delivery/partners/{id}", partnerId)
                        .with(authentication(authFor(true)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
