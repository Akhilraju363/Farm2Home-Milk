package com.farm2home.farm.controller;

import com.farm2home.farm.config.GatewayHeaderAuthFilter;
import com.farm2home.farm.config.SecurityConfig;
import com.farm2home.farm.dto.request.CreateFarmRequest;
import com.farm2home.farm.dto.response.FarmResponse;
import com.farm2home.farm.service.impl.FarmServiceImpl;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmController.class)
@Import(SecurityConfig.class)
class FarmControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private FarmServiceImpl farmService;

    private final UUID farmId = UUID.randomUUID();

    // farm-service's own GatewayHeaderAuthFilter prepends "ROLE_" to every role it reads
    // from X-User-Roles (unlike some sibling services' filters) - hasAnyRole(...) in
    // FarmController is written to match that, so the grant here needs the prefix too.
    private UsernamePasswordAuthenticationToken manager() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER")));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    private FarmResponse buildResponse() {
        return FarmResponse.builder().id(farmId).farmName("Green Meadows").ownerName("Ravi").build();
    }

    @Test
    @DisplayName("POST /farm → 201 for FARM_MANAGER")
    void create_manager_ok() throws Exception {
        CreateFarmRequest req = new CreateFarmRequest();
        req.setFarmName("Green Meadows");
        req.setOwnerName("Ravi");
        when(farmService.create(any())).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/farm")
                        .with(authentication(manager()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.farmName").value("Green Meadows"));
    }

    @Test
    @DisplayName("POST /farm → 403 for non-manager CUSTOMER")
    void create_customer_forbidden() throws Exception {
        CreateFarmRequest req = new CreateFarmRequest();
        req.setFarmName("Green Meadows");
        req.setOwnerName("Ravi");

        mockMvc.perform(post("/api/v1/farm")
                        .with(authentication(customer()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /farm → 200")
    void findAll_ok() throws Exception {
        when(farmService.findAll(any())).thenReturn(
                new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/farm").with(authentication(customer())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /farm/{id} → 200")
    void findById_ok() throws Exception {
        when(farmService.findById(farmId)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/farm/{id}", farmId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(farmId.toString()));
    }

    @Test
    @DisplayName("DELETE /farm/{id} → 200 for FARM_MANAGER")
    void delete_manager_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/farm/{id}", farmId).with(authentication(manager())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /farm/{id}/image → 200 for FARM_MANAGER")
    void uploadImage_manager_ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "farm.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(farmService.uploadImage(eq(farmId), any())).thenReturn(buildResponse());

        mockMvc.perform(multipart("/api/v1/farm/{id}/image", farmId)
                        .file(file)
                        .with(authentication(manager())))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("GET /api/v1/farm/search")
    class Search {

        @Test
        @DisplayName("no @PreAuthorize on this endpoint → 200 for any authenticated user")
        void customer_ok() throws Exception {
            when(farmService.search(any(), any(), any(), any())).thenReturn(
                    new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/farm/search")
                            .param("keyword", "meadows")
                            .with(authentication(customer())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(farmId.toString()));
        }
    }
}
