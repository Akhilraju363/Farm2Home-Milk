package com.farm2home.order.domain.repository;

import com.farm2home.order.domain.entity.SubscriptionSnapshot;
import com.farm2home.order.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionSnapshotRepository extends JpaRepository<SubscriptionSnapshot, UUID> {

    List<SubscriptionSnapshot> findAllByStatus(SubscriptionStatus status);

    List<SubscriptionSnapshot> findAllByCustomerIdAndStatus(UUID customerId, SubscriptionStatus status);
}
