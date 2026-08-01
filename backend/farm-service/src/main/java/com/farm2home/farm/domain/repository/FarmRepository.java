package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.Farm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FarmRepository extends JpaRepository<Farm, UUID>, JpaSpecificationExecutor<Farm> {

    Optional<Farm> findByIdAndDeletedFalse(UUID id);

    Page<Farm> findAllByDeletedFalse(Pageable pageable);
}
