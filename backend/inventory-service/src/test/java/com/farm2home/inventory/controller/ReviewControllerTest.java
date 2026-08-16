package com.farm2home.inventory.controller;

import com.farm2home.inventory.config.GatewayHeaderAuthFilter;
import com.farm2home.inventory.config.SecurityConfig;
import com.farm2home.inventory.config.UserPrincipal;
import com.farm2home.inventory.dto.request.CreateReviewRequest;
import com.farm2home.inventory.dto.request.UpdateReviewRequest;
import com.farm2home.inventory.dto.response.ReviewResponse;
import com.farm2home.inventory.service.impl.ReviewServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
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

/** No "no bearer token -> 401" test here - matches LocationMasterControllerTest's documented
 *  convention: a @WebMvcTest slice has no gateway filter in front of it, so an anonymous request
 *  surfaces Spring Security's default 403, not the 401 the gateway actually produces live. */
@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ReviewServiceImpl reviewService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken tokenFor(UUID userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "9000000001", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private ReviewResponse sampleResponse() {
        return ReviewResponse.builder().id(reviewId).productId(UUID.randomUUID()).productName("Full Cream Milk 1L")
                .customerId(customerId).rating(5).reviewText("Great!").build();
    }

    private String validCreateJson() throws Exception {
        CreateReviewRequest r = new CreateReviewRequest();
        r.setOrderId(UUID.randomUUID());
        r.setProductId(UUID.randomUUID());
        r.setRating(5);
        r.setReviewText("Fresh and on time.");
        return objectMapper.writeValueAsString(r);
    }

    @Nested
    @DisplayName("POST /api/v1/reviews")
    class Create {

        @Test
        @DisplayName("CUSTOMER can submit a review - 201")
        void customer_canCreate() throws Exception {
            when(reviewService.create(eq(customerId), any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/reviews")
                            .with(authentication(tokenFor(customerId, "CUSTOMER")))
                            .contentType("application/json").content(validCreateJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.rating").value(5));
        }

        @ParameterizedTest
        @ValueSource(strings = {"SUPER_ADMIN", "FARM_MANAGER", "DELIVERY_MANAGER", "DELIVERY_PARTNER"})
        @DisplayName("non-CUSTOMER roles cannot submit a review - 403")
        void nonCustomer_forbidden(String role) throws Exception {
            mockMvc.perform(post("/api/v1/reviews")
                            .with(authentication(tokenFor(UUID.randomUUID(), role)))
                            .contentType("application/json").content(validCreateJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("missing rating -> 400")
        void missingRating_badRequest() throws Exception {
            CreateReviewRequest r = new CreateReviewRequest();
            r.setOrderId(UUID.randomUUID());
            r.setProductId(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/reviews")
                            .with(authentication(tokenFor(customerId, "CUSTOMER")))
                            .contentType("application/json").content(objectMapper.writeValueAsString(r)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rating out of range -> 400")
        void ratingOutOfRange_badRequest() throws Exception {
            CreateReviewRequest r = new CreateReviewRequest();
            r.setOrderId(UUID.randomUUID());
            r.setProductId(UUID.randomUUID());
            r.setRating(6);

            mockMvc.perform(post("/api/v1/reviews")
                            .with(authentication(tokenFor(customerId, "CUSTOMER")))
                            .contentType("application/json").content(objectMapper.writeValueAsString(r)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reviews/my")
    class FindMy {

        @Test
        @DisplayName("CUSTOMER can list own reviews - 200")
        void customer_canList() throws Exception {
            when(reviewService.findMyReviews(eq(customerId), any()))
                    .thenReturn(new PageImpl<>(List.of(sampleResponse()), org.springframework.data.domain.PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/reviews/my").with(authentication(tokenFor(customerId, "CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(reviewId.toString()));
        }

        @Test
        @DisplayName("SUPER_ADMIN cannot use the customer self-service endpoint - 403")
        void admin_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/reviews/my").with(authentication(tokenFor(UUID.randomUUID(), "SUPER_ADMIN"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/reviews/{id}")
    class Update {

        @Test
        @DisplayName("CUSTOMER can edit own review - 200")
        void customer_canUpdate() throws Exception {
            UpdateReviewRequest r = new UpdateReviewRequest();
            r.setRating(4);
            when(reviewService.update(eq(customerId), eq(reviewId), any())).thenReturn(sampleResponse());

            mockMvc.perform(put("/api/v1/reviews/{id}", reviewId)
                            .with(authentication(tokenFor(customerId, "CUSTOMER")))
                            .contentType("application/json").content(objectMapper.writeValueAsString(r)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/reviews/{id}")
    class Delete {

        @Test
        @DisplayName("CUSTOMER can delete - 200")
        void customer_canDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).with(authentication(tokenFor(customerId, "CUSTOMER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("SUPER_ADMIN can delete (moderation) - 200")
        void admin_canDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId)
                            .with(authentication(tokenFor(UUID.randomUUID(), "SUPER_ADMIN"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("FARM_MANAGER can delete (moderation) - 200")
        void farmManager_canDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId)
                            .with(authentication(tokenFor(UUID.randomUUID(), "FARM_MANAGER"))))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"DELIVERY_MANAGER", "DELIVERY_PARTNER"})
        @DisplayName("DELIVERY_MANAGER/DELIVERY_PARTNER cannot delete - 403")
        void otherRoles_forbidden(String role) throws Exception {
            mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId)
                            .with(authentication(tokenFor(UUID.randomUUID(), role))))
                    .andExpect(status().isForbidden());
        }
    }
}
