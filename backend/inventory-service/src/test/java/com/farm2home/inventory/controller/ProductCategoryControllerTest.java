package com.farm2home.inventory.controller;

import com.farm2home.inventory.config.GatewayHeaderAuthFilter;
import com.farm2home.inventory.config.SecurityConfig;
import com.farm2home.inventory.dto.request.CreateProductCategoryRequest;
import com.farm2home.inventory.dto.response.ProductCategoryResponse;
import com.farm2home.inventory.exception.DuplicateResourceException;
import com.farm2home.inventory.service.impl.ProductCategoryServiceImpl;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductCategoryController.class)
@Import(SecurityConfig.class)
class ProductCategoryControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ProductCategoryServiceImpl service;

    private final UUID categoryId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    private ProductCategoryResponse buildResponse() {
        return ProductCategoryResponse.builder().id(categoryId).name("Milk").active(true).build();
    }

    @Test
    @DisplayName("POST /product-categories → 201 for SUPER_ADMIN")
    void create_admin_ok() throws Exception {
        CreateProductCategoryRequest req = new CreateProductCategoryRequest();
        req.setName("Milk");
        when(service.create(any())).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/inventory/product-categories")
                        .with(authentication(admin()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Milk"));
    }

    @Test
    @DisplayName("POST /product-categories → 403 for non-admin CUSTOMER")
    void create_customer_forbidden() throws Exception {
        CreateProductCategoryRequest req = new CreateProductCategoryRequest();
        req.setName("Milk");

        mockMvc.perform(post("/api/v1/inventory/product-categories")
                        .with(authentication(customer()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /product-categories → 400 for blank name")
    void create_blankName_badRequest() throws Exception {
        CreateProductCategoryRequest req = new CreateProductCategoryRequest();
        req.setName("  ");

        mockMvc.perform(post("/api/v1/inventory/product-categories")
                        .with(authentication(admin()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /product-categories → 409 for duplicate name")
    void create_duplicateName_conflict() throws Exception {
        CreateProductCategoryRequest req = new CreateProductCategoryRequest();
        req.setName("Milk");
        when(service.create(any())).thenThrow(new DuplicateResourceException("A category named 'Milk' already exists"));

        mockMvc.perform(post("/api/v1/inventory/product-categories")
                        .with(authentication(admin()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /product-categories → 200 for authenticated CUSTOMER")
    void findAll_ok() throws Exception {
        when(service.findAll(eq(true), any())).thenReturn(
                new PageImpl<>(List.of(buildResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/inventory/product-categories").with(authentication(customer())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /product-categories/{id} → 200")
    void findById_ok() throws Exception {
        when(service.findById(categoryId)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/inventory/product-categories/{id}", categoryId).with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(categoryId.toString()));
    }

    @Test
    @DisplayName("PUT /product-categories/{id} → 200 for SUPER_ADMIN")
    void update_admin_ok() throws Exception {
        when(service.update(eq(categoryId), any())).thenReturn(buildResponse());

        mockMvc.perform(put("/api/v1/inventory/product-categories/{id}", categoryId)
                        .with(authentication(admin()))
                        .contentType("application/json")
                        .content("{\"description\":\"Fresh dairy milk\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /product-categories/{id} → 403 for non-admin CUSTOMER")
    void update_customer_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/product-categories/{id}", categoryId)
                        .with(authentication(customer()))
                        .contentType("application/json")
                        .content("{\"description\":\"Fresh dairy milk\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /product-categories/{id} → 200 for SUPER_ADMIN")
    void delete_admin_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/product-categories/{id}", categoryId).with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /product-categories/{id} → 403 for non-admin CUSTOMER")
    void delete_customer_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/product-categories/{id}", categoryId).with(authentication(customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /product-categories/{id} → 409 when still assigned to a live product")
    void delete_categoryInUse_conflict() throws Exception {
        org.mockito.Mockito.doThrow(new com.farm2home.inventory.exception.CategoryInUseException(
                        "This category cannot be deleted because it is assigned to existing products"))
                .when(service).delete(categoryId);

        mockMvc.perform(delete("/api/v1/inventory/product-categories/{id}", categoryId).with(authentication(admin())))
                .andExpect(status().isConflict());
    }
}
