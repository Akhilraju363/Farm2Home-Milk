package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.HealthRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {

    Page<HealthRecord> findAllByCowIdAndDeletedFalseOrderByRecordDateDesc(UUID cowId, Pageable pageable);
    Optional<HealthRecord> findTopByCowIdAndDeletedFalseOrderByRecordDateDesc(UUID cowId);
    Optional<HealthRecord> findByIdAndCowIdAndDeletedFalse(UUID id, UUID cowId);
}
