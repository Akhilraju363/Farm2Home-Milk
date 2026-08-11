package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.LocationCity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LocationCityRepository extends JpaRepository<LocationCity, UUID> {

    List<LocationCity> findAllByDistrictIdAndActiveTrueOrderByNameAsc(UUID districtId);
}
