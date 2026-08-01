package com.farm2home.notification.controller;

import com.farm2home.notification.config.GatewayHeaderAuthFilter;
import com.farm2home.notification.config.SecurityConfig;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.service.impl.NotificationServiceImpl;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationLogController.class)
@Import(SecurityConfig.class)
class NotificationLogControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationServiceImpl service;

    private final UUID recipientId = UUID.randomUUID();
    private final UUID logId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(boolean admin) {
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("FARM_MANAGER"))
                : List.of(new SimpleGrantedAuthority("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken("9876543210", null, authorities);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/logs - admin authority → 200")
    void findByRecipient_admin_ok() throws Exception {
        when(service.findByRecipient(any(), any())).thenReturn(
                new PageImpl<>(List.of(NotificationLogResponse.builder().id(logId).build()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications/logs")
                        .with(authentication(authFor(true)))
                        .param("recipientId", recipientId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/logs - non-admin authority → 403")
    void findByRecipient_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/logs")
                        .with(authentication(authFor(false)))
                        .param("recipientId", recipientId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/logs/{id} - admin authority → 200")
    void findById_admin_ok() throws Exception {
        when(service.findById(logId)).thenReturn(NotificationLogResponse.builder().id(logId).build());

        mockMvc.perform(get("/api/v1/notifications/logs/{id}", logId).with(authentication(authFor(true))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/summary - admin authority → 200")
    void summary_admin_ok() throws Exception {
        when(service.getRecent(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/summary").with(authentication(authFor(true))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/summary - non-admin authority → 403")
    void summary_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/summary").with(authentication(authFor(false))))
                .andExpect(status().isForbidden());
    }
}
