package com.farm2home.delivery.controller;

import com.farm2home.delivery.config.GatewayHeaderAuthFilter;
import com.farm2home.delivery.config.SecurityConfig;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.service.DeliveryAssignmentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryAssignmentController.class)
@Import(SecurityConfig.class)
class DeliveryAssignmentControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DeliveryAssignmentService assignmentService;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID partnerUserId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(UUID userId, boolean admin) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("DELIVERY_PARTNER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_PARTNER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Nested
    @DisplayName("POST /api/v1/delivery/assignments")
    class ManualAssign {

        @Test
        @DisplayName("admin role → 201")
        void admin_created() throws Exception {
            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(UUID.randomUUID());
            req.setDeliveryPartnerId(UUID.randomUUID());
            req.setRouteId(UUID.randomUUID());
            when(assignmentService.manualAssign(any())).thenReturn(
                    AssignmentResponse.builder().id(assignmentId).build());

            mockMvc.perform(post("/api/v1/delivery/assignments")
                            .with(authentication(authFor(UUID.randomUUID(), true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("non-admin role → 403")
        void nonAdmin_forbidden() throws Exception {
            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(UUID.randomUUID());
            req.setDeliveryPartnerId(UUID.randomUUID());
            req.setRouteId(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/delivery/assignments")
                            .with(authentication(authFor(UUID.randomUUID(), false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments")
    class FindAll {

        @Test
        @DisplayName("delivery partner → passes own userId and isAdmin=false")
        void partner_ownIdNotAdmin() throws Exception {
            when(assignmentService.findAll(eq(partnerUserId), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.of(AssignmentResponse.builder().id(assignmentId).build()),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/delivery/assignments").with(authentication(authFor(partnerUserId, false))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/delivery/assignments/{id}")
    class FindById {

        @Test
        @DisplayName("existing assignment → 200")
        void found() throws Exception {
            when(assignmentService.findById(eq(assignmentId), eq(partnerUserId), eq(false)))
                    .thenReturn(AssignmentResponse.builder().id(assignmentId).build());

            mockMvc.perform(get("/api/v1/delivery/assignments/{id}", assignmentId)
                            .with(authentication(authFor(partnerUserId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/delivery/assignments/{id}/status")
    class UpdateStatus {

        @Test
        @DisplayName("valid transition → 200")
        void validTransition_ok() throws Exception {
            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.DELIVERED);
            req.setFailureReason("n/a");
            req.setDeliveryProof("signed-by-customer");
            when(assignmentService.updateStatus(eq(assignmentId), any(), eq(partnerUserId), eq(false)))
                    .thenReturn(AssignmentResponse.builder().id(assignmentId).status("DELIVERED").build());

            mockMvc.perform(patch("/api/v1/delivery/assignments/{id}/status", assignmentId)
                            .with(authentication(authFor(partnerUserId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DELIVERED"));
        }
    }
}
