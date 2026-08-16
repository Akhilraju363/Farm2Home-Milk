package com.farm2home.order.domain.repository;

import com.farm2home.order.domain.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    /** Ownership-scoped lookup - a CartItem id that exists but belongs to someone else's cart
     *  must 404, not leak whether the id exists (same convention used everywhere else in this
     *  codebase for owned resources). */
    Optional<CartItem> findByIdAndCart_CustomerId(UUID id, UUID customerId);

    Optional<CartItem> findByCart_IdAndProductId(UUID cartId, UUID productId);
}
