package com.farm2home.order.service;

import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface OrderService {

    /** Create a manual ONE_TIME order. */
    OrderResponse createManualOrder(CreateOrderRequest request, UUID customerId);

    /** List orders. {@code null} customerId returns all (admin). */
    Page<OrderResponse> findAll(UUID customerId, Pageable pageable);

    /** Get order by ID. Ownership enforced unless customerId is null (admin). */
    OrderResponse findById(UUID id, UUID customerId);

    /** Get all orders for a specific subscription. */
    Page<OrderResponse> findBySubscription(UUID subscriptionId, Pageable pageable);

    /**
     * Transition order status.
     * Validates allowed transitions; DELIVERY_PARTNER can only move to DELIVERED.
     * actorId identifies who performed the change for audit purposes - customerId is null for
     * admin calls (it means "don't scope the lookup to one customer"), so actorId is passed
     * separately rather than reused for that.
     */
    OrderResponse updateStatus(UUID id, UpdateOrderStatusRequest request, UUID customerId, boolean isAdmin,
            UUID actorId);

    /** Soft-cancel an order. See updateStatus for why actorId is separate from customerId. */
    void cancel(UUID id, UUID customerId, boolean isAdmin, UUID actorId);
}
