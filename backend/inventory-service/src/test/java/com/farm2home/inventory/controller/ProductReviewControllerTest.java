package com.farm2home.inventory.controller;

import com.farm2home.inventory.config.GatewayHeaderAuthFilter;
import com.farm2home.inventory.config.SecurityConfig;
import com.farm2home.inventory.config.UserPrincipal;
import com.farm2home.inventory.dto.response.RatingSummaryResponse;
import com.farm2home.inventory.dto.response.ReviewResponse;
import com.farm2home.inventory.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductReviewController.class)
@Import(SecurityConfig.class)
class ProductReviewControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
            return new GatewayHeaderAuthFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private ReviewServiceImpl reviewService;

    private final UUID productId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken tokenFor(String role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9000000001", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPER_ADMIN", "FARM_MANAGER", "DELIVERY_MANAGER", "DELIVERY_PARTNER"})
    @DisplayName("any authenticated role can list a product's reviews - 200")
    void anyRole_canListReviews(String role) throws Exception {
        ReviewResponse review = ReviewResponse.builder().id(UUID.randomUUID()).productId(productId).rating(5).build();
        when(reviewService.findByProduct(eq(productId), any()))
                .thenReturn(new PageImpl<>(List.of(review), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/inventory/products/{productId}/reviews", productId).with(authentication(tokenFor(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rating").value(5));
    }

    @Test
    @DisplayName("rating summary reflects the true aggregate, not a page - 200")
    void ratingSummary_returnsAggregate() throws Exception {
        RatingSummaryResponse summary = RatingSummaryResponse.builder()
                .productId(productId).averageRating(new BigDecimal("4.3")).totalReviews(27)
                .rating1Count(0).rating2Count(1).rating3Count(2).rating4Count(8).rating5Count(16)
                .build();
        when(reviewService.ratingSummary(productId)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/inventory/products/{productId}/rating-summary", productId)
                        .with(authentication(tokenFor("CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.3))
                .andExpect(jsonPath("$.data.totalReviews").value(27));
    }
}
