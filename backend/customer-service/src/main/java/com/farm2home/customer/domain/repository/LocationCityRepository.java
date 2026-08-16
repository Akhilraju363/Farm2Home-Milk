package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.LocationCity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationCityRepository extends JpaRepository<LocationCity, UUID> {

    List<LocationCity> findAllByDistrictIdAndActiveTrueOrderByNameAsc(UUID districtId);
    List<LocationCity> findAllByDistrictIdOrderByNameAsc(UUID districtId);
    boolean existsByDistrictIdAndNameIgnoreCase(UUID districtId, String name);
    boolean existsByDistrictIdAndNameIgnoreCaseAndIdNot(UUID districtId, String name, UUID id);
    boolean existsByDistrictIdAndActiveTrue(UUID districtId);
    Optional<LocationCity> findByDistrictIdAndNameIgnoreCaseAndActiveTrue(UUID districtId, String name);
}
