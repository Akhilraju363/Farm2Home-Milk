package com.farm2home.invoice.domain.repository;

import com.farm2home.invoice.domain.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByIdAndDeletedFalse(UUID id);

    Optional<Invoice> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    Optional<Invoice> findByOrderIdAndDeletedFalse(UUID orderId);

    Optional<Invoice> findByOrderIdAndCustomerIdAndDeletedFalse(UUID orderId, UUID customerId);

    boolean existsByOrderIdAndDeletedFalse(UUID orderId);

    Page<Invoice> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    @Query(value = "SELECT nextval('invoice.invoice_number_seq')", nativeQuery = true)
    Long nextInvoiceNumber();
}
