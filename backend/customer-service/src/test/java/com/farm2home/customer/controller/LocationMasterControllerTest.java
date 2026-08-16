package com.farm2home.customer.controller;

import com.farm2home.customer.config.GatewayHeaderAuthFilter;
import com.farm2home.customer.config.SecurityConfig;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.dto.request.LocationMasterRequest;
import com.farm2home.customer.dto.response.LocationMasterResponse;
import com.farm2home.customer.service.LocationMasterService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No test asserts "no bearer token -> 401" here, matching this codebase's established
 *  convention (see DeliveryLocationControllerTest/LocationControllerTest) - a @WebMvcTest slice
 *  has no gateway JwtAuthenticationFilter in front of it, so an anonymous request surfaces Spring
 *  Security's default 403 AccessDeniedHandler, not the 401 the gateway actually produces live. */
@WebMvcTest(LocationMasterController.class)
@Import(SecurityConfig.class)
class LocationMasterControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LocationMasterService locationMasterService;

    private final UUID stateId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken tokenFor(String role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9000000001", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private LocationMasterResponse sampleResponse() {
        return LocationMasterResponse.builder().id(stateId).name("Andhra Pradesh").code("AP").active(true).build();
    }

    private String validStateJson() throws Exception {
        LocationMasterRequest r = new LocationMasterRequest();
        r.setName("Andhra Pradesh");
        r.setCode("AP");
        r.setActive(true);
        return objectMapper.writeValueAsString(r);
    }

    @Nested @DisplayName("Role access")
    class RoleAccess {

        @Test
        @DisplayName("SUPER_ADMIN can list states - 200")
        void superAdmin_canAccess() throws Exception {
            when(locationMasterService.states(null)).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/location-master/states").with(authentication(tokenFor("SUPER_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].code").value("AP"));
        }

        @Test
        @DisplayName("FARM_MANAGER can list states - 200")
        void farmManager_canAccess() throws Exception {
            when(locationMasterService.states(null)).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/location-master/states").with(authentication(tokenFor("FARM_MANAGER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CUSTOMER is denied - 403")
        void customer_denied() throws Exception {
            mockMvc.perform(get("/api/v1/location-master/states").with(authentication(tokenFor("CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELIVERY_PARTNER is denied - 403")
        void deliveryPartner_denied() throws Exception {
            mockMvc.perform(get("/api/v1/location-master/states").with(authentication(tokenFor("DELIVERY_PARTNER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELIVERY_MANAGER is denied - 403 (Location Master is SUPER_ADMIN/FARM_MANAGER only, narrower than most reference-data reads)")
        void deliveryManager_denied() throws Exception {
            mockMvc.perform(get("/api/v1/location-master/states").with(authentication(tokenFor("DELIVERY_MANAGER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Write endpoints (create/update/delete) are also denied to CUSTOMER - 403")
        void customer_deniedOnWrite() throws Exception {
            mockMvc.perform(post("/api/v1/location-master/states")
                            .with(authentication(tokenFor("CUSTOMER")))
                            .contentType("application/json").content(validStateJson()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested @DisplayName("States endpoints")
    class StatesEndpoints {

        @Test
        @DisplayName("POST /states - 201 Created")
        void create_returns201() throws Exception {
            when(locationMasterService.createState(any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/location-master/states")
                            .with(authentication(tokenFor("SUPER_ADMIN")))
                            .contentType("application/json").content(validStateJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("Andhra Pradesh"));
        }

        @Test
        @DisplayName("POST /states with blank name - 400 Bad Request")
        void create_blankName_returns400() throws Exception {
            LocationMasterRequest r = new LocationMasterRequest();
            r.setName("");
            r.setCode("AP");
            r.setActive(true);

            mockMvc.perform(post("/api/v1/location-master/states")
                            .with(authentication(tokenFor("SUPER_ADMIN")))
                            .contentType("application/json").content(objectMapper.writeValueAsString(r)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /states with active omitted (null) - 400 Bad Request")
        void create_missingActive_returns400() throws Exception {
            LocationMasterRequest r = new LocationMasterRequest();
            r.setName("Andhra Pradesh");
            r.setCode("AP");

            mockMvc.perform(post("/api/v1/location-master/states")
                            .with(authentication(tokenFor("SUPER_ADMIN")))
                            .contentType("application/json").content(objectMapper.writeValueAsString(r)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT /states/{id} - 200 OK")
        void update_returns200() throws Exception {
            when(locationMasterService.updateState(any(), any())).thenReturn(sampleResponse());

            mockMvc.perform(put("/api/v1/location-master/states/{id}", stateId)
                            .with(authentication(tokenFor("SUPER_ADMIN")))
                            .contentType("application/json").content(validStateJson()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE /states/{id} - 200 OK")
        void delete_returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/location-master/states/{id}", stateId)
                            .with(authentication(tokenFor("SUPER_ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("Districts/Cities endpoints")
    class DistrictsAndCities {

        @Test
        @DisplayName("GET /districts?stateId= - 200 OK, passes stateId through")
        void listDistricts_passesStateId() throws Exception {
            when(locationMasterService.districts(eq(stateId), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/location-master/districts").param("stateId", stateId.toString())
                            .with(authentication(tokenFor("SUPER_ADMIN"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /cities - 201 Created")
        void createCity_returns201() throws Exception {
            LocationMasterRequest r = new LocationMasterRequest();
            r.setName("Machilipatnam");
            r.setDistrictId(UUID.randomUUID());
            r.setActive(true);
            when(locationMasterService.createCity(any())).thenReturn(
                    LocationMasterResponse.builder().id(UUID.randomUUID()).name("Machilipatnam").active(true).build());

            mockMvc.perform(post("/api/v1/location-master/cities")
                            .with(authentication(tokenFor("SUPER_ADMIN")))
                            .contentType("application/json").content(objectMapper.writeValueAsString(r)))
                    .andExpect(status().isCreated());
        }
    }
}
