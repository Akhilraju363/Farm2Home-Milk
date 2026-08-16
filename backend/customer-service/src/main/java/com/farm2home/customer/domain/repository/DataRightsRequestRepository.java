package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.DataRightsRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DataRightsRequestRepository extends JpaRepository<DataRightsRequest, UUID> {

    Page<DataRightsRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
