package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.ConsentRecord;
import com.farm2home.customer.domain.enums.ConsentPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findAllByCustomerId(UUID customerId);

    Optional<ConsentRecord> findByCustomerIdAndPurpose(UUID customerId, ConsentPurpose purpose);
}
