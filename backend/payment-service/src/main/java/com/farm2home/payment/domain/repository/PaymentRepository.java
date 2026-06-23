package com.farm2home.payment.domain.repository;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndDeletedFalse(UUID id);
    Optional<Payment> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);
    Optional<Payment> findByPaymentReferenceAndDeletedFalse(String paymentReference);

    List<Payment> findAllByOrderIdAndDeletedFalse(UUID orderId);

    Page<Payment> findAllByDeletedFalse(Pageable pageable);
    Page<Payment> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    boolean existsByOrderIdAndPaymentStatusAndDeletedFalse(UUID orderId, PaymentStatus status);
}
