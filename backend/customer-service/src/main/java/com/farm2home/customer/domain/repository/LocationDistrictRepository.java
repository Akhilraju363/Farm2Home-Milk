package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.LocationDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationDistrictRepository extends JpaRepository<LocationDistrict, UUID> {

    List<LocationDistrict> findAllByStateIdAndActiveTrueOrderByNameAsc(UUID stateId);

    /** Scopes the lookup to the given district id only (never a state id) - a district belonging
     *  to a different state is never returned as "found" by this, so callers can't accidentally
     *  treat a district as valid for the wrong state's cascading dropdown. */
    Optional<LocationDistrict> findByIdAndActiveTrue(UUID id);
}
