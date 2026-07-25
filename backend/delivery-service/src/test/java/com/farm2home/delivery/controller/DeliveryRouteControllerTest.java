package com.farm2home.delivery.controller;

import com.farm2home.delivery.config.GatewayHeaderAuthFilter;
import com.farm2home.delivery.config.SecurityConfig;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.response.RouteResponse;
import com.farm2home.delivery.service.impl.DeliveryRouteServiceImpl;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryRouteController.class)
@Import(SecurityConfig.class)
class DeliveryRouteControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DeliveryRouteServiceImpl routeService;

    private final UUID routeId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Nested
    @DisplayName("POST /api/v1/delivery/routes")
    class Create {

        @Test
        @DisplayName("admin role → 201")
        void admin_created() throws Exception {
            CreateRouteRequest req = new CreateRouteRequest();
            req.setRouteName("North Zone");
            req.setRouteCode("NZ1");
            req.setArea("North");
            req.setCity("Metropolis");
            req.setPincode("560001");
            when(routeService.create(any())).thenReturn(RouteResponse.builder().id(routeId).routeCode("NZ1").build());

            mockMvc.perform(post("/api/v1/delivery/routes")
                            .with(authentication(authFor(true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.routeCode").value("NZ1"));
        }

        @Test
        @DisplayName("non-admin role → 403")
        void nonAdmin_forbidden() throws Exception {
            CreateRouteRequest req = new CreateRouteRequest();
            req.setRouteName("North Zone");
            req.setRouteCode("NZ1");
            req.setArea("North");
            req.setCity("Metropolis");
            req.setPincode("560001");

            mockMvc.perform(post("/api/v1/delivery/routes")
                            .with(authentication(authFor(false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/routes")
    class FindAll {

        @Test
        @DisplayName("any authenticated user → 200")
        void anyUser_ok() throws Exception {
            when(routeService.findAll(any())).thenReturn(
                    new PageImpl<>(List.of(RouteResponse.builder().id(routeId).build()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/delivery/routes").with(authentication(authFor(false))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/routes/{id}")
    class FindById {

        @Test
        @DisplayName("existing route → 200")
        void found() throws Exception {
            when(routeService.findById(routeId)).thenReturn(RouteResponse.builder().id(routeId).build());

            mockMvc.perform(get("/api/v1/delivery/routes/{id}", routeId).with(authentication(authFor(false))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/delivery/routes/{id}")
    class Delete {

        @Test
        @DisplayName("admin role → 200")
        void admin_ok() throws Exception {
            mockMvc.perform(delete("/api/v1/delivery/routes/{id}", routeId).with(authentication(authFor(true))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("non-admin role → 403")
        void nonAdmin_forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/delivery/routes/{id}", routeId).with(authentication(authFor(false))))
                    .andExpect(status().isForbidden());
        }
    }
}
