package com.farm2home.customer.controller;

import com.farm2home.customer.config.GatewayHeaderAuthFilter;
import com.farm2home.customer.config.SecurityConfig;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.domain.enums.DataRightsRequestStatus;
import com.farm2home.customer.domain.enums.DataRightsRequestType;
import com.farm2home.customer.dto.response.DataRightsRequestResponse;
import com.farm2home.customer.service.impl.DataRightsRequestServiceImpl;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataRightsRequestController.class)
@Import(SecurityConfig.class)
class DataRightsRequestControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private DataRightsRequestServiceImpl service;

    private UsernamePasswordAuthenticationToken tokenFor(String role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9000000001", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    @DisplayName("anonymous (no bearer token) can submit a request - 201")
    void anonymous_canSubmit() throws Exception {
        DataRightsRequestResponse response = DataRightsRequestResponse.builder()
                .id(UUID.randomUUID()).requesterName("Jane Doe").requestType(DataRightsRequestType.ACCESS)
                .status(DataRightsRequestStatus.NEW).build();
        when(service.create(any(), any())).thenReturn(response);

        String body = """
                {"requesterName":"Jane Doe","requesterContact":"jane@example.com","requestType":"ACCESS"}
                """;

        mockMvc.perform(post("/api/v1/data-rights-requests/submit")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requesterName").value("Jane Doe"));
    }

    @Test
    @DisplayName("missing requesterContact -> 400")
    void missingContact_badRequest() throws Exception {
        String body = """
                {"requesterName":"Jane Doe","requestType":"ACCESS"}
                """;

        mockMvc.perform(post("/api/v1/data-rights-requests/submit")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("non-admin cannot list requests - 403")
    void nonAdmin_cannotList() throws Exception {
        mockMvc.perform(get("/api/v1/data-rights-requests").with(authentication(tokenFor("CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN can list requests - 200")
    void admin_canList() throws Exception {
        when(service.findAll(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/data-rights-requests").with(authentication(tokenFor("SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }
}
