package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.enums.CowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CowRepository extends JpaRepository<Cow, UUID> {

    Page<Cow> findAllByDeletedFalse(Pageable pageable);
    Page<Cow> findAllByStatusAndDeletedFalse(CowStatus status, Pageable pageable);
    Optional<Cow> findByIdAndDeletedFalse(UUID id);
    Optional<Cow> findByTagNumberAndDeletedFalse(String tagNumber);
    boolean existsByTagNumberAndDeletedFalse(String tagNumber);
    long countByStatusAndDeletedFalse(CowStatus status);
}
