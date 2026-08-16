package com.farm2home.customer.controller;

import com.farm2home.customer.config.GatewayHeaderAuthFilter;
import com.farm2home.customer.config.SecurityConfig;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.dto.response.ConsentResponse;
import com.farm2home.customer.service.impl.ConsentServiceImpl;
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

@WebMvcTest(ConsentController.class)
@Import(SecurityConfig.class)
class ConsentControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ConsentServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken token() {
        UserPrincipal principal = new UserPrincipal(customerId, "9000000001", Set.of("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @Test
    @DisplayName("no bearer token -> 403 (unauthenticated)")
    void noAuth_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me/consents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("authenticated customer can record consent choices - 200")
    void authenticated_canRecord() throws Exception {
        ConsentResponse response = ConsentResponse.builder().purpose(com.farm2home.customer.domain.enums.ConsentPurpose.MARKETING_COMMUNICATIONS).granted(true).build();
        when(service.upsert(eq(customerId), any(), any(), any(), any())).thenReturn(List.of(response));

        String body = """
                {"consents":[{"purpose":"MARKETING_COMMUNICATIONS","granted":true,"noticeVersion":"v1"}]}
                """;

        mockMvc.perform(post("/api/v1/customers/me/consents")
                        .with(authentication(token()))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].purpose").value("MARKETING_COMMUNICATIONS"));
    }

    @Test
    @DisplayName("authenticated customer can list their own consents - 200")
    void authenticated_canList() throws Exception {
        when(service.findAll(customerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customers/me/consents").with(authentication(token())))
                .andExpect(status().isOk());
    }
}
