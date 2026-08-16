package com.farm2home.inventory.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl {

    private static final String DELIVERED_STATUS = "DELIVERED";

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderServiceClient orderServiceClient;
    private final CustomerServiceClient customerServiceClient;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "Review")
    public ReviewResponse create(UUID customerId, CreateReviewRequest request) {
        Product product = getProduct(request.getProductId());
        OrderDetailResponse order = orderServiceClient.getOrder(request.getOrderId()).block();
        // Anti-enumeration: an order that doesn't exist and one that exists but belongs to
        // someone else look identical to the caller - both are "Order not found" (404), never a
        // 403 that would confirm the order exists (same convention as order-service's own
        // findOrder(id, customerId)).
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new ResourceNotFoundException("Order not found: " + request.getOrderId());
        }
        if (!DELIVERED_STATUS.equals(order.getStatus())) {
            throw new InventoryException("Only delivered orders can be reviewed.");
        }
        if (reviewRepository.existsByCustomerIdAndOrderIdAndProductIdAndDeletedFalse(
                customerId, request.getOrderId(), request.getProductId())) {
            throw new ConflictException("You have already reviewed this product for this order.");
        }

        Review review = Review.builder()
                .productId(request.getProductId())
                .customerId(customerId)
                .orderId(request.getOrderId())
                .rating(request.getRating())
                .reviewText(request.getReviewText())
                .build();
        return compose(reviewRepository.save(review), product, null);
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Review")
    public ReviewResponse update(UUID customerId, UUID reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findByIdAndCustomerIdAndDeletedFalse(reviewId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        review.setRating(request.getRating());
        review.setReviewText(request.getReviewText());
        return compose(reviewRepository.save(review), getProduct(review.getProductId()), null);
    }

    /** Admins may delete/moderate any review; a customer may only delete their own - both paths
     *  resolve to the same "Review not found" 404 on mismatch (see create()'s anti-enumeration note). */
    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "Review")
    public void delete(UserPrincipal principal, UUID reviewId) {
        Review review = principal.isAdmin()
                ? reviewRepository.findByIdAndDeletedFalse(reviewId)
                        .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId))
                : reviewRepository.findByIdAndCustomerIdAndDeletedFalse(reviewId, principal.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        review.setDeleted(true);
        reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> findMyReviews(UUID customerId, Pageable pageable) {
        Page<Review> page = reviewRepository.findAllByCustomerIdAndDeletedFalse(customerId, pageable);
        Map<UUID, Product> products = loadProducts(page);
        return page.map(r -> compose(r, products.get(r.getProductId()), null));
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> findByProduct(UUID productId, Pageable pageable) {
        Product product = getProduct(productId);
        Page<Review> page = reviewRepository.findAllByProductIdAndDeletedFalse(productId, pageable);
        // Built with a plain HashMap, not Collectors.toMap - resolveDisplayName legitimately
        // returns null (customer lookup failed, or has no firstName), and Collectors.toMap throws
        // NPE on a null value (it delegates to HashMap.merge, which rejects nulls).
        Map<UUID, String> displayNames = new HashMap<>();
        page.getContent().stream().map(Review::getCustomerId).distinct()
                .forEach(id -> displayNames.put(id, resolveDisplayName(id)));
        return page.map(r -> compose(r, product, displayNames.get(r.getCustomerId())));
    }

    @Transactional(readOnly = true)
    public RatingSummaryResponse ratingSummary(UUID productId) {
        getProduct(productId);
        // ratingSummaryRaw's JPQL has multiple select expressions, so the JPA provider always
        // returns List<Object[]> under the hood regardless of the declared method return type -
        // declaring it as a single Object[] made Spring Data wrap that one-row list AS an
        // Object[] (i.e. Object[]{ realRow }), not unwrap it, so row[1] onward threw
        // ArrayIndexOutOfBoundsException. An aggregate query with no GROUP BY always returns
        // exactly one row (COUNT(0)/SUM(null) if nothing matches), so .get(0) is always safe.
        Object[] row = reviewRepository.ratingSummaryRaw(productId).get(0);
        long total = row[1] == null ? 0L : (Long) row[1];
        // AVG() over an integer column comes back as a Double from PostgreSQL/Hibernate, not a
        // BigDecimal - cast via Number rather than assuming either concrete type.
        BigDecimal average = (row[0] == null || total == 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(((Number) row[0]).doubleValue()).setScale(1, RoundingMode.HALF_UP);
        return RatingSummaryResponse.builder()
                .productId(productId)
                .averageRating(average)
                .totalReviews(total)
                .rating1Count(countOf(row[2]))
                .rating2Count(countOf(row[3]))
                .rating3Count(countOf(row[4]))
                .rating4Count(countOf(row[5]))
                .rating5Count(countOf(row[6]))
                .build();
    }

    private long countOf(Object value) {
        return value == null ? 0L : (Long) value;
    }

    private Product getProduct(UUID productId) {
        return productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private Map<UUID, Product> loadProducts(Page<Review> page) {
        // Same null-value issue as findByProduct's displayNames map - a product can be
        // soft-deleted after a review of it exists, so this must tolerate a null value.
        Map<UUID, Product> products = new HashMap<>();
        page.getContent().stream().map(Review::getProductId).distinct()
                .forEach(id -> products.put(id, productRepository.findByIdAndDeletedFalse(id).orElse(null)));
        return products;
    }

    /** First name + last initial only - never the full name or any contact detail, so a
     *  product's public review list can't be used to identify a customer. */
    private String resolveDisplayName(UUID customerId) {
        CustomerDetailResponse customer = customerServiceClient.getCustomer(customerId).block();
        if (customer == null || customer.getFirstName() == null) {
            return null;
        }
        String lastInitial = (customer.getLastName() != null && !customer.getLastName().isBlank())
                ? " " + customer.getLastName().charAt(0) + "."
                : "";
        return customer.getFirstName() + lastInitial;
    }

    private ReviewResponse compose(Review review, Product product, String customerDisplayName) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .productName(product != null ? product.getName() : null)
                .customerId(review.getCustomerId())
                .customerDisplayName(customerDisplayName)
                .orderId(review.getOrderId())
                .rating(review.getRating())
                .reviewText(review.getReviewText())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
