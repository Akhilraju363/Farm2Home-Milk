package com.farm2home.customer.controller;

import com.farm2home.customer.config.GatewayHeaderAuthFilter;
import com.farm2home.customer.config.SecurityConfig;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.dto.response.LocationCityResponse;
import com.farm2home.customer.dto.response.LocationDistrictResponse;
import com.farm2home.customer.dto.response.LocationStateResponse;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.service.impl.LocationServiceImpl;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No test asserts "no bearer token -> 401" here, matching this codebase's established
 *  convention (see DeliveryLocationControllerTest) - a @WebMvcTest slice has no gateway
 *  JwtAuthenticationFilter in front of it, so an anonymous request surfaces Spring Security's
 *  default 403 AccessDeniedHandler, not the 401 the gateway actually produces live. */
@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
class LocationControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private LocationServiceImpl locationService;

    private final UUID stateId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();

    // Location reference data has no role restriction (matches FarmController's GET /farm
    // convention) - a plain CUSTOMER mid-registration is exactly who calls this.
    private UsernamePasswordAuthenticationToken customer() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @Test
    @DisplayName("GET /states - any authenticated caller (e.g. CUSTOMER) can list states")
    void getStates_returnsStates() throws Exception {
        when(locationService.getStates()).thenReturn(List.of(
                LocationStateResponse.builder().id(stateId).name("Andhra Pradesh").code("AP").build()));

        mockMvc.perform(get("/api/v1/locations/states").with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("AP"));
    }

    @Test
    @DisplayName("GET /states/{stateId}/districts - returns districts for a valid state")
    void getDistricts_validState_returnsDistricts() throws Exception {
        when(locationService.getDistricts(stateId)).thenReturn(List.of(
                LocationDistrictResponse.builder().id(districtId).name("Krishna").code("AP-04").build()));

        mockMvc.perform(get("/api/v1/locations/states/{stateId}/districts", stateId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Krishna"));
    }

    @Test
    @DisplayName("GET /states/{stateId}/districts - unknown state id propagates as 404")
    void getDistricts_invalidState_returns404() throws Exception {
        UUID badId = UUID.randomUUID();
        when(locationService.getDistricts(eq(badId))).thenThrow(new ResourceNotFoundException("No state found with id: " + badId));

        mockMvc.perform(get("/api/v1/locations/states/{stateId}/districts", badId).with(authentication(customer())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /districts/{districtId}/cities - returns cities for a valid district")
    void getCities_validDistrict_returnsCities() throws Exception {
        UUID cityId = UUID.randomUUID();
        when(locationService.getCities(districtId)).thenReturn(List.of(
                LocationCityResponse.builder().id(cityId).name("Machilipatnam").build()));

        mockMvc.perform(get("/api/v1/locations/districts/{districtId}/cities", districtId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Machilipatnam"));
    }

    @Test
    @DisplayName("GET /districts/{districtId}/cities - unknown district id propagates as 404")
    void getCities_invalidDistrict_returns404() throws Exception {
        UUID badId = UUID.randomUUID();
        when(locationService.getCities(eq(badId))).thenThrow(new ResourceNotFoundException("No district found with id: " + badId));

        mockMvc.perform(get("/api/v1/locations/districts/{districtId}/cities", badId).with(authentication(customer())))
                .andExpect(status().isNotFound());
    }
}
