package com.farm2home.subscription.service;

import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionService {

    /**
     * Create a subscription for the given customer.
     */
    SubscriptionResponse create(CreateSubscriptionRequest request, UUID customerId);

    /**
     * List subscriptions. Pass {@code null} as {@code customerId} to return all (admin use).
     */
    Page<SubscriptionResponse> findAll(UUID customerId, Pageable pageable);

    /**
     * Get a single subscription.
     * Ownership is enforced: non-null {@code customerId} must own the record.
     */
    SubscriptionResponse findById(UUID id, UUID customerId);

    /**
     * Update mutable fields of an ACTIVE subscription.
     */
    SubscriptionResponse update(UUID id, UpdateSubscriptionRequest request, UUID customerId);

    /**
     * Soft-cancel a subscription (sets status = CANCELLED).
     */
    void cancel(UUID id, UUID customerId);

    /**
     * Pause an ACTIVE subscription until the requested date.
     */
    SubscriptionResponse pause(UUID id, PauseSubscriptionRequest request, UUID customerId);

    /**
     * Resume a PAUSED subscription immediately.
     */
    SubscriptionResponse resume(UUID id, UUID customerId);
}
