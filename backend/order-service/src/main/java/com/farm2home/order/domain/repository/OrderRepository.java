package com.farm2home.order.domain.repository;

import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAllByDeletedFalse(Pageable pageable);

    Page<Order> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    Page<Order> findAllByCustomerIdAndStatusAndDeletedFalse(UUID customerId, OrderStatus status, Pageable pageable);

    Page<Order> findAllByStatusAndDeletedFalse(OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndDeletedFalse(UUID id);

    Optional<Order> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsBySubscriptionIdAndOrderDateAndDeletedFalse(UUID subscriptionId, LocalDate orderDate);

    Page<Order> findAllBySubscriptionIdAndDeletedFalse(UUID subscriptionId, Pageable pageable);

    @Query(value = "SELECT nextval('\"order\".order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();
}
