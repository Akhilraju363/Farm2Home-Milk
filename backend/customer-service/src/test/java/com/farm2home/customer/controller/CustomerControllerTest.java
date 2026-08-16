package com.farm2home.customer.controller;

import com.farm2home.customer.config.GatewayHeaderAuthFilter;
import com.farm2home.customer.config.SecurityConfig;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.common.core.analytics.CustomerGrowthPoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.customer.dto.request.CreateAddressRequest;
import com.farm2home.customer.dto.request.UpdateAddressRequest;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.AddressResponse;
import com.farm2home.customer.dto.response.CustomerResponse;
import com.farm2home.customer.service.impl.CustomerServiceImpl;
import com.farm2home.customer.service.impl.DeliveryAvailabilityServiceImpl;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CustomerServiceImpl customerService;
    @MockBean private DeliveryAvailabilityServiceImpl deliveryAvailabilityService;

    private final UUID customerId = UUID.randomUUID();

    // Principal object matches what GatewayHeaderAuthFilter actually builds in production
    // (UserPrincipal, not a bare String) - @AuthenticationPrincipal UserPrincipal on the
    // controller methods requires this to resolve correctly.
    private UsernamePasswordAuthenticationToken admin() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9000000001", Set.of("SUPER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
    }

    // FARM_MANAGER can search customers (unlike GET /customers list/export, still SUPER_ADMIN/
    // DELIVERY_MANAGER only) so they can look up a customer while managing that customer's
    // subscriptions from subscription-service - see UserPrincipal.isAdmin()'s comment.
    private UsernamePasswordAuthenticationToken farmManager() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9000000002", Set.of("FARM_MANAGER"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("FARM_MANAGER")));
    }

    /** A CUSTOMER accessing their own record (id == customerId), the common case exercised by
     *  most of this file's non-ownership-specific tests. */
    private UsernamePasswordAuthenticationToken customer() {
        UserPrincipal principal = new UserPrincipal(customerId, "9876543210", Set.of("CUSTOMER"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    private CustomerResponse buildResponse() {
        return CustomerResponse.builder().id(customerId).customerCode("CUST-001000")
                .firstName("Kafka").lastName("Tester").status("ACTIVE").build();
    }

    @Test
    @DisplayName("GET /customers → 200 for SUPER_ADMIN")
    void findAll_admin_ok() throws Exception {
        when(customerService.findAll(any())).thenReturn(
                new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/customers").with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /customers → 403 for non-admin CUSTOMER")
    void findAll_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customers").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /customers/{id} → 200")
    void findById_ok() throws Exception {
        when(customerService.findById(eq(customerId), any())).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/customers/{id}", customerId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerId.toString()));
    }

    @Test
    @DisplayName("PUT /customers/{id} → 200")
    void update_ok() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setEmail("new@example.com");
        when(customerService.update(eq(customerId), any(), any())).thenReturn(buildResponse());

        mockMvc.perform(put("/api/v1/customers/{id}", customerId)
                        .with(authentication(customer()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /customers/{id} → 200 for SUPER_ADMIN")
    void delete_admin_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/{id}", customerId).with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /customers/{id} → 403 for non-admin CUSTOMER")
    void delete_customer_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/{id}", customerId).with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /customers/summary → 200 for SUPER_ADMIN")
    void getSummary_admin_ok() throws Exception {
        when(customerService.getSummary()).thenReturn(
                CustomerSummaryResponse.builder().totalCustomers(42L).build());

        mockMvc.perform(get("/api/v1/customers/summary").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(42));
    }

    @Test
    @DisplayName("GET /customers/summary → 403 for non-admin CUSTOMER")
    void getSummary_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customers/summary").with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /customers/{id}/profile-image → 200")
    void uploadProfileImage_ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(customerService.uploadProfileImage(eq(customerId), any(), any())).thenReturn(buildResponse());

        mockMvc.perform(multipart("/api/v1/customers/{id}/profile-image", customerId)
                        .file(file)
                        .with(authentication(customer())))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("GET /api/v1/customers/reports")
    class GetReport {

        @Test
        @DisplayName("SUPER_ADMIN → 200")
        void admin_ok() throws Exception {
            when(customerService.getReport(any(), any(), any(), any())).thenReturn(
                    ReportPage.<com.farm2home.common.core.reports.CustomerReportRow, CustomerReportSummary>builder()
                            .content(List.of())
                            .pageNumber(0).pageSize(20).totalElements(0).totalPages(0)
                            .summary(CustomerReportSummary.builder()
                                    .totalCustomers(0).activeCustomers(0).inactiveCustomers(0).suspendedCustomers(0).build())
                            .build());

            mockMvc.perform(get("/api/v1/customers/reports").with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.totalCustomers").value(0));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/customers/reports").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/search")
    class Search {

        @Test
        @DisplayName("SUPER_ADMIN → 200")
        void admin_ok() throws Exception {
            when(customerService.search(any(), any(), any(), any(), any())).thenReturn(
                    new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/customers/search")
                            .param("keyword", "kafka")
                            .with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(customerId.toString()));
        }

        @Test
        @DisplayName("FARM_MANAGER → 200")
        void farmManager_ok() throws Exception {
            when(customerService.search(any(), any(), any(), any(), any())).thenReturn(
                    new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/customers/search")
                            .param("keyword", "kafka")
                            .with(authentication(farmManager())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/customers/search").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/analytics/growth-trend")
    class GetGrowthTrend {

        @Test
        @DisplayName("SUPER_ADMIN → 200")
        void admin_ok() throws Exception {
            when(customerService.getGrowthTrend(eq(Granularity.DAILY), any(), any())).thenReturn(
                    TrendSeries.<CustomerGrowthPoint>builder()
                            .granularity(Granularity.DAILY)
                            .points(List.of(CustomerGrowthPoint.builder()
                                    .period(java.time.LocalDate.of(2026, 1, 1))
                                    .newCustomers(5L).build()))
                            .build());

            mockMvc.perform(get("/api/v1/customers/analytics/growth-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points[0].newCustomers").value(5));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/customers/analytics/growth-trend")
                            .param("granularity", "DAILY")
                            .with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("/api/v1/customers/{id}/addresses")
    class Addresses {

        private AddressResponse buildAddressResponse() {
            return AddressResponse.builder().id(UUID.randomUUID()).addressLine1("402, Block A")
                    .city("Mumbai").state("Maharashtra").pincode("400001").defaultAddress(true).build();
        }

        @Test
        @DisplayName("GET → 200 for the owning customer")
        void list_owner_ok() throws Exception {
            when(customerService.getAddresses(eq(customerId), any())).thenReturn(List.of(buildAddressResponse()));

            mockMvc.perform(get("/api/v1/customers/{id}/addresses", customerId).with(authentication(customer())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].city").value("Mumbai"));
        }

        @Test
        @DisplayName("POST → 201")
        void create_ok() throws Exception {
            CreateAddressRequest req = new CreateAddressRequest();
            req.setAddressLine1("402, Block A");
            req.setCity("Mumbai");
            req.setState("Maharashtra");
            req.setPincode("400001");
            when(customerService.addAddressScoped(eq(customerId), any(), any())).thenReturn(buildAddressResponse());

            mockMvc.perform(post("/api/v1/customers/{id}/addresses", customerId)
                            .with(authentication(customer()))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("PUT /{addressId} → 200")
        void update_ok() throws Exception {
            UUID addressId = UUID.randomUUID();
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setCity("Pune");
            when(customerService.updateAddress(eq(customerId), eq(addressId), any(), any())).thenReturn(buildAddressResponse());

            mockMvc.perform(put("/api/v1/customers/{id}/addresses/{addressId}", customerId, addressId)
                            .with(authentication(customer()))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE /{addressId} → 200")
        void delete_ok() throws Exception {
            UUID addressId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/customers/{id}/addresses/{addressId}", customerId, addressId)
                            .with(authentication(customer())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /{addressId}/default → 200")
        void setDefault_ok() throws Exception {
            UUID addressId = UUID.randomUUID();
            when(customerService.setDefaultAddress(eq(customerId), eq(addressId), any())).thenReturn(buildAddressResponse());

            mockMvc.perform(patch("/api/v1/customers/{id}/addresses/{addressId}/default", customerId, addressId)
                            .with(authentication(customer())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/export")
    class Export {

        @Test
        @DisplayName("SUPER_ADMIN, CSV format → 200 with attachment headers")
        void admin_csv_ok() throws Exception {
            doNothing().when(customerService).export(any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/customers/export")
                            .param("format", "CSV")
                            .with(authentication(admin())))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv"))
                    .andExpect(header().exists("Content-Disposition"));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/customers/export").param("format", "CSV").with(authentication(customer())))
                    .andExpect(status().isForbidden());
        }
    }
}
