package com.farm2home.subscription.domain.repository;

import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Page<Subscription> findAllByDeletedFalse(Pageable pageable);

    Page<Subscription> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    Optional<Subscription> findByIdAndDeletedFalse(UUID id);

    Optional<Subscription> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    boolean existsByCustomerIdAndStatusAndDeletedFalse(UUID customerId, SubscriptionStatus status);

    @Modifying
    @Query("""
           UPDATE Subscription s
           SET s.status = 'EXPIRED'
           WHERE s.endDate < :today
             AND s.status = 'ACTIVE'
             AND s.deleted = false
           """)
    int expireByEndDate(@Param("today") LocalDate today);
}
