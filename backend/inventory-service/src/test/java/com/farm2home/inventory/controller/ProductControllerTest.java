package com.farm2home.inventory.controller;

import com.farm2home.inventory.config.GatewayHeaderAuthFilter;
import com.farm2home.inventory.config.SecurityConfig;
import com.farm2home.inventory.domain.enums.ProductUnit;
import com.farm2home.inventory.dto.request.CreateProductRequest;
import com.farm2home.inventory.dto.response.ProductResponse;
import com.farm2home.inventory.service.impl.ProductServiceImpl;
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

import java.math.BigDecimal;
import java.util.List;
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

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ProductServiceImpl service;

    private final UUID productId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    private ProductResponse buildResponse() {
        return ProductResponse.builder().id(productId).name("Full Cream Milk")
                .price(new BigDecimal("80.00")).unit("L").active(true).build();
    }

    @Test
    @DisplayName("POST /products → 201 for SUPER_ADMIN")
    void create_admin_ok() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Full Cream Milk");
        req.setPrice(new BigDecimal("80.00"));
        req.setUnit(ProductUnit.L);
        when(service.create(any())).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/inventory/products")
                        .with(authentication(admin()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Full Cream Milk"));
    }

    @Test
    @DisplayName("POST /products → 403 for non-admin CUSTOMER")
    void create_customer_forbidden() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Full Cream Milk");
        req.setPrice(new BigDecimal("80.00"));
        req.setUnit(ProductUnit.L);

        mockMvc.perform(post("/api/v1/inventory/products")
                        .with(authentication(customer()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /products → 200")
    void findAll_ok() throws Exception {
        when(service.findAll(eq(true), any())).thenReturn(
                new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/inventory/products").with(authentication(customer())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /products/{id} → 200")
    void findById_ok() throws Exception {
        when(service.findById(productId)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/inventory/products/{id}", productId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId.toString()));
    }

    @Test
    @DisplayName("DELETE /products/{id} → 200 for SUPER_ADMIN")
    void delete_admin_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/products/{id}", productId).with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /products/{id}/image → 200 for SUPER_ADMIN")
    void uploadImage_admin_ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "milk.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(service.uploadImage(eq(productId), any())).thenReturn(buildResponse());

        mockMvc.perform(multipart("/api/v1/inventory/products/{id}/image", productId)
                        .file(file)
                        .with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("GET /api/v1/inventory/products/search")
    class Search {

        @Test
        @DisplayName("authenticated but unprivileged user → 200")
        void customer_ok() throws Exception {
            when(service.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(
                    new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/inventory/products/search")
                            .param("keyword", "milk")
                            .with(authentication(customer())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(productId.toString()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inventory/products/export")
    class Export {

        @Test
        @DisplayName("authenticated, CSV format → 200 with attachment headers")
        void authenticated_csv_ok() throws Exception {
            doNothing().when(service).export(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());

            MvcResult started = mockMvc.perform(get("/api/v1/inventory/products/export")
                            .param("format", "CSV")
                            .with(authentication(customer())))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(started))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv"))
                    .andExpect(header().exists("Content-Disposition"));
        }
    }
}
