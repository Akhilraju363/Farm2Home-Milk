package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.LocationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationStateRepository extends JpaRepository<LocationState, UUID> {

    List<LocationState> findAllByActiveTrueOrderByNameAsc();

    Optional<LocationState> findByIdAndActiveTrue(UUID id);
}
