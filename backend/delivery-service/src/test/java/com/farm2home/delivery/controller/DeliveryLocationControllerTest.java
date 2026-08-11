package com.farm2home.delivery.controller;

import com.farm2home.delivery.config.GatewayHeaderAuthFilter;
import com.farm2home.delivery.config.SecurityConfig;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.dto.request.SubmitLocationRequest;
import com.farm2home.delivery.dto.response.LocationResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.service.impl.DeliveryLocationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryLocationController.class)
@Import(SecurityConfig.class)
class DeliveryLocationControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DeliveryLocationServiceImpl locationService;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken authFor(UUID id, String role) {
        UserPrincipal principal = new UserPrincipal(id, "9876543210", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private LocationResponse buildResponse() {
        return LocationResponse.builder().deliveryAssignmentId(assignmentId)
                .latitude(17.4123).longitude(78.4482).recordedAt(LocalDateTime.now()).freshness("LIVE").build();
    }

    @Test
    @DisplayName("POST .../location → 200 for the owning delivery partner")
    void submitLocation_ok() throws Exception {
        SubmitLocationRequest req = new SubmitLocationRequest();
        req.setLatitude(17.4123);
        req.setLongitude(78.4482);
        when(locationService.submitLocation(eq(assignmentId), any(), eq(userId))).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/delivery/assignments/{id}/location", assignmentId)
                        .with(authentication(authFor(userId, "DELIVERY_PARTNER")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freshness").value("LIVE"));
    }

    @Test
    @DisplayName("POST .../location → 400 when the request has an out-of-range latitude")
    void submitLocation_invalidLatitude_badRequest() throws Exception {
        SubmitLocationRequest req = new SubmitLocationRequest();
        req.setLatitude(200.0);
        req.setLongitude(78.4482);

        mockMvc.perform(post("/api/v1/delivery/assignments/{id}/location", assignmentId)
                        .with(authentication(authFor(userId, "DELIVERY_PARTNER")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST .../location → 400 when the assignment is not OUT_FOR_DELIVERY")
    void submitLocation_wrongStatus_badRequest() throws Exception {
        SubmitLocationRequest req = new SubmitLocationRequest();
        req.setLatitude(17.0);
        req.setLongitude(78.0);
        when(locationService.submitLocation(eq(assignmentId), any(), eq(userId)))
                .thenThrow(new DeliveryException("Location can only be submitted while the assignment is OUT_FOR_DELIVERY"));

        mockMvc.perform(post("/api/v1/delivery/assignments/{id}/location", assignmentId)
                        .with(authentication(authFor(userId, "DELIVERY_PARTNER")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST .../location → 404 for a non-owning caller")
    void submitLocation_notOwner_notFound() throws Exception {
        SubmitLocationRequest req = new SubmitLocationRequest();
        req.setLatitude(17.0);
        req.setLongitude(78.0);
        when(locationService.submitLocation(eq(assignmentId), any(), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Assignment not found: " + assignmentId));

        mockMvc.perform(post("/api/v1/delivery/assignments/{id}/location", assignmentId)
                        .with(authentication(authFor(userId, "DELIVERY_PARTNER")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET .../location → 200 with null data when tracking hasn't started")
    void getCurrentLocation_empty_ok() throws Exception {
        when(locationService.getCurrentLocation(eq(assignmentId), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/delivery/assignments/{id}/location", assignmentId)
                        .with(authentication(authFor(userId, "CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET .../location → 404 for a caller who doesn't own this assignment")
    void getCurrentLocation_notOwner_notFound() throws Exception {
        when(locationService.getCurrentLocation(eq(assignmentId), any()))
                .thenThrow(new ResourceNotFoundException("Assignment not found: " + assignmentId));

        mockMvc.perform(get("/api/v1/delivery/assignments/{id}/location", assignmentId)
                        .with(authentication(authFor(userId, "CUSTOMER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET .../locations → 200 with a paginated history")
    void getHistory_ok() throws Exception {
        when(locationService.getHistory(eq(assignmentId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/delivery/assignments/{id}/locations", assignmentId)
                        .with(authentication(authFor(userId, "SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].latitude").value(17.4123));
    }

    @Test
    @DisplayName("no token → rejected (403 here; the gateway's JwtAuthenticationFilter is what returns " +
            "401 for a genuinely missing token live - this slice test has no gateway in front of it, so an " +
            "anonymous request against .authenticated() surfaces as Spring Security's default AccessDeniedHandler)")
    void noToken_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/assignments/{id}/location", assignmentId))
                .andExpect(status().isForbidden());
    }
}
