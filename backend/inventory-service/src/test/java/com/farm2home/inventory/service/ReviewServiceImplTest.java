package com.farm2home.inventory.service;

import com.farm2home.common.web.exception.ConflictException;
import com.farm2home.inventory.client.CustomerDetailResponse;
import com.farm2home.inventory.client.CustomerServiceClient;
import com.farm2home.inventory.client.OrderDetailResponse;
import com.farm2home.inventory.client.OrderServiceClient;
import com.farm2home.inventory.config.UserPrincipal;
import com.farm2home.inventory.domain.entity.Product;
import com.farm2home.inventory.domain.entity.Review;
import com.farm2home.inventory.domain.repository.ProductRepository;
import com.farm2home.inventory.domain.repository.ReviewRepository;
import com.farm2home.inventory.dto.request.CreateReviewRequest;
import com.farm2home.inventory.dto.request.UpdateReviewRequest;
import com.farm2home.inventory.dto.response.RatingSummaryResponse;
import com.farm2home.inventory.dto.response.ReviewResponse;
import com.farm2home.inventory.exception.InventoryException;
import com.farm2home.inventory.exception.ResourceNotFoundException;
import com.farm2home.inventory.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private CustomerServiceClient customerServiceClient;

    @InjectMocks private ReviewServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID otherCustomerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(productId).name("Full Cream Milk 1L").build();
    }

    private OrderDetailResponse order(UUID ownerCustomerId, String status) {
        OrderDetailResponse o = new OrderDetailResponse();
        o.setId(orderId);
        o.setCustomerId(ownerCustomerId);
        o.setStatus(status);
        return o;
    }

    private CreateReviewRequest createRequest() {
        CreateReviewRequest r = new CreateReviewRequest();
        r.setOrderId(orderId);
        r.setProductId(productId);
        r.setRating(5);
        r.setReviewText("Great milk, fresh every day.");
        return r;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("valid delivered order, no existing review -> review saved")
        void validRequest_savesReview() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(order(customerId, "DELIVERED")));
            when(reviewRepository.existsByCustomerIdAndOrderIdAndProductIdAndDeletedFalse(customerId, orderId, productId))
                    .thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            ReviewResponse response = service.create(customerId, createRequest());

            assertThat(response.getProductId()).isEqualTo(productId);
            assertThat(response.getProductName()).isEqualTo("Full Cream Milk 1L");
            assertThat(response.getRating()).isEqualTo(5);
            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("order belongs to a different customer -> 404 (anti-enumeration, not 403)")
        void wrongCustomer_notFound() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(order(otherCustomerId, "DELIVERED")));

            assertThatThrownBy(() -> service.create(customerId, createRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("order does not exist -> 404")
        void orderNotFound_notFound() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.empty());

            assertThatThrownBy(() -> service.create(customerId, createRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("order not yet DELIVERED -> 400")
        void notDelivered_badRequest() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(order(customerId, "OUT_FOR_DELIVERY")));

            assertThatThrownBy(() -> service.create(customerId, createRequest()))
                    .isInstanceOf(InventoryException.class);
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("duplicate review for same customer+order+product -> 409")
        void duplicate_conflict() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(order(customerId, "DELIVERED")));
            when(reviewRepository.existsByCustomerIdAndOrderIdAndProductIdAndDeletedFalse(customerId, orderId, productId))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.create(customerId, createRequest()))
                    .isInstanceOf(ConflictException.class);
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("product does not exist -> 404")
        void productNotFound_notFound() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> service.create(customerId, createRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(orderServiceClient);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("own review -> updated")
        void ownReview_updated() {
            UUID reviewId = UUID.randomUUID();
            Review existing = Review.builder().id(reviewId).productId(productId).customerId(customerId)
                    .orderId(orderId).rating(3).reviewText("ok").build();
            when(reviewRepository.findByIdAndCustomerIdAndDeletedFalse(reviewId, customerId))
                    .thenReturn(java.util.Optional.of(existing));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));

            UpdateReviewRequest request = new UpdateReviewRequest();
            request.setRating(4);
            request.setReviewText("Better than I thought.");

            ReviewResponse response = service.update(customerId, reviewId, request);

            assertThat(response.getRating()).isEqualTo(4);
            assertThat(response.getReviewText()).isEqualTo("Better than I thought.");
        }

        @Test
        @DisplayName("someone else's review -> 404, not editable")
        void otherCustomersReview_notFound() {
            UUID reviewId = UUID.randomUUID();
            when(reviewRepository.findByIdAndCustomerIdAndDeletedFalse(reviewId, customerId))
                    .thenReturn(java.util.Optional.empty());

            UpdateReviewRequest request = new UpdateReviewRequest();
            request.setRating(1);

            assertThatThrownBy(() -> service.update(customerId, reviewId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("customer deletes own review -> succeeds")
        void customer_deletesOwn() {
            UUID reviewId = UUID.randomUUID();
            Review existing = Review.builder().id(reviewId).customerId(customerId).build();
            UserPrincipal principal = new UserPrincipal(customerId, "9000000001", Set.of("CUSTOMER"));
            when(reviewRepository.findByIdAndCustomerIdAndDeletedFalse(reviewId, customerId))
                    .thenReturn(java.util.Optional.of(existing));

            service.delete(principal, reviewId);

            assertThat(existing.isDeleted()).isTrue();
            verify(reviewRepository).save(existing);
        }

        @Test
        @DisplayName("customer cannot delete someone else's review -> 404")
        void customer_cannotDeleteOthers() {
            UUID reviewId = UUID.randomUUID();
            UserPrincipal principal = new UserPrincipal(customerId, "9000000001", Set.of("CUSTOMER"));
            when(reviewRepository.findByIdAndCustomerIdAndDeletedFalse(reviewId, customerId))
                    .thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> service.delete(principal, reviewId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("SUPER_ADMIN can delete any review (moderation)")
        void admin_deletesAnyReview() {
            UUID reviewId = UUID.randomUUID();
            Review existing = Review.builder().id(reviewId).customerId(otherCustomerId).build();
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9000000002", Set.of("SUPER_ADMIN"));
            when(reviewRepository.findByIdAndDeletedFalse(reviewId)).thenReturn(java.util.Optional.of(existing));

            service.delete(admin, reviewId);

            assertThat(existing.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("ratingSummary")
    class RatingSummary {

        @Test
        @DisplayName("no reviews yet -> zero average, zero counts")
        void noReviews_zeroed() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(reviewRepository.ratingSummaryRaw(productId))
                    .thenReturn(List.<Object[]>of(new Object[]{null, 0L, 0L, 0L, 0L, 0L, 0L}));

            RatingSummaryResponse summary = service.ratingSummary(productId);

            assertThat(summary.getTotalReviews()).isZero();
            assertThat(summary.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("aggregates every active review, not just one page")
        void withReviews_aggregatesAll() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(reviewRepository.ratingSummaryRaw(productId))
                    .thenReturn(List.<Object[]>of(new Object[]{new BigDecimal("4.3333333333"), 3L, 0L, 0L, 0L, 1L, 2L}));

            RatingSummaryResponse summary = service.ratingSummary(productId);

            assertThat(summary.getTotalReviews()).isEqualTo(3L);
            assertThat(summary.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.3"));
            assertThat(summary.getRating5Count()).isEqualTo(2L);
            assertThat(summary.getRating4Count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("unknown product -> 404")
        void unknownProduct_notFound() {
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> service.ratingSummary(productId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByProduct")
    class FindByProduct {

        @Test
        @DisplayName("resolves product name and customer display name for each review")
        void resolvesDisplayNames() {
            Pageable pageable = PageRequest.of(0, 10);
            Review review = Review.builder().id(UUID.randomUUID()).productId(productId).customerId(customerId)
                    .orderId(orderId).rating(5).reviewText("Nice").build();
            when(productRepository.findByIdAndDeletedFalse(productId)).thenReturn(java.util.Optional.of(product));
            when(reviewRepository.findAllByProductIdAndDeletedFalse(productId, pageable))
                    .thenReturn(new PageImpl<>(List.of(review), pageable, 1));
            CustomerDetailResponse customer = new CustomerDetailResponse();
            customer.setFirstName("Akhil");
            customer.setLastName("Raju");
            when(customerServiceClient.getCustomer(customerId)).thenReturn(Mono.just(customer));

            Page<ReviewResponse> page = service.findByProduct(productId, pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getProductName()).isEqualTo("Full Cream Milk 1L");
            assertThat(page.getContent().get(0).getCustomerDisplayName()).isEqualTo("Akhil R.");
        }
    }
}
