package com.farm2home.subscription.controller;

import com.farm2home.subscription.config.GatewayHeaderAuthFilter;
import com.farm2home.subscription.config.SecurityConfig;
import com.farm2home.subscription.config.UserPrincipal;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.service.SubscriptionService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SubscriptionService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID subId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(UUID userId, boolean admin) {
        UserPrincipal principal = new UserPrincipal(userId, "9876543210",
                admin ? Set.of("FARM_MANAGER") : Set.of("CUSTOMER"));
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private SubscriptionResponse buildResponse(String status) {
        return SubscriptionResponse.builder().id(subId).customerId(customerId).status(status).build();
    }

    @Nested
    @DisplayName("POST /api/v1/subscriptions")
    class Create {

        @Test
        @DisplayName("customer → uses own principal id regardless of body customerId")
        void customer_usesOwnId() throws Exception {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.5"))
                    .scheduleType(ScheduleType.DAILY).startDate(LocalDate.now()).build();
            when(service.create(any(), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(post("/api/v1/subscriptions")
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(service).create(any(), eq(customerId));
        }

        @Test
        @DisplayName("admin with explicit customerId → uses request's customerId")
        void admin_usesRequestCustomerId() throws Exception {
            UUID targetCustomer = UUID.randomUUID();
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .customerId(targetCustomer)
                    .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.5"))
                    .scheduleType(ScheduleType.DAILY).startDate(LocalDate.now()).build();
            when(service.create(any(), eq(targetCustomer))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(post("/api/v1/subscriptions")
                            .with(authentication(authFor(UUID.randomUUID(), true)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(service).create(any(), eq(targetCustomer));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/subscriptions/{id}")
    class GetById {

        @Test
        @DisplayName("customer → filtered by own id")
        void customer_filtered() throws Exception {
            when(service.findById(eq(subId), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(get("/api/v1/subscriptions/{id}", subId).with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("admin → no customer filter (null)")
        void admin_noFilter() throws Exception {
            when(service.findById(eq(subId), isNull())).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(get("/api/v1/subscriptions/{id}", subId).with(authentication(authFor(UUID.randomUUID(), true))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/subscriptions/{id}")
    class Update {

        @Test
        @DisplayName("valid request → 200")
        void validRequest_ok() throws Exception {
            UpdateSubscriptionRequest req = UpdateSubscriptionRequest.builder()
                    .quantity(new BigDecimal("2.0")).build();
            when(service.update(eq(subId), any(), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(put("/api/v1/subscriptions/{id}", subId)
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/subscriptions/{id}")
    class Cancel {

        @Test
        @DisplayName("valid cancel → 200")
        void cancel_ok() throws Exception {
            mockMvc.perform(delete("/api/v1/subscriptions/{id}", subId).with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk());

            verify(service).cancel(subId, customerId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/subscriptions/{id}/pause and /resume")
    class PauseResume {

        @Test
        @DisplayName("pause → 200")
        void pause_ok() throws Exception {
            PauseSubscriptionRequest req = new PauseSubscriptionRequest(LocalDate.now().plusDays(7));
            when(service.pause(eq(subId), any(), eq(customerId))).thenReturn(buildResponse("PAUSED"));

            mockMvc.perform(post("/api/v1/subscriptions/{id}/pause", subId)
                            .with(authentication(authFor(customerId, false)))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAUSED"));
        }

        @Test
        @DisplayName("resume → 200")
        void resume_ok() throws Exception {
            when(service.resume(eq(subId), eq(customerId))).thenReturn(buildResponse("ACTIVE"));

            mockMvc.perform(post("/api/v1/subscriptions/{id}/resume", subId)
                            .with(authentication(authFor(customerId, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }
    }
}
