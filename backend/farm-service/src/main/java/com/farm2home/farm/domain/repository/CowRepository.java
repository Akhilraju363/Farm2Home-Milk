package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.enums.CowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CowRepository extends JpaRepository<Cow, UUID> {

    Page<Cow> findAllByDeletedFalse(Pageable pageable);
    Page<Cow> findAllByStatusAndDeletedFalse(CowStatus status, Pageable pageable);
    Page<Cow> findAllByFarmIdAndDeletedFalse(UUID farmId, Pageable pageable);
    Page<Cow> findAllByFarmIdAndStatusAndDeletedFalse(UUID farmId, CowStatus status, Pageable pageable);
    Optional<Cow> findByIdAndDeletedFalse(UUID id);
    Optional<Cow> findByTagNumberAndDeletedFalse(String tagNumber);
    boolean existsByTagNumberAndDeletedFalse(String tagNumber);
    long countByStatusAndDeletedFalse(CowStatus status);

    // Id-only projection so cross-service filter resolution (e.g. reports-service resolving a
    // farm filter into cow ids for production-service) doesn't transfer full Cow payloads.
    @Query("SELECT c.id FROM Cow c WHERE c.farmId = :farmId AND c.deleted = false")
    List<UUID> findIdsByFarmIdAndDeletedFalse(@Param("farmId") UUID farmId);
}
