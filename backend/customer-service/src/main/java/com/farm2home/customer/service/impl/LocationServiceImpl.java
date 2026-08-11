package com.farm2home.customer.service.impl;

import com.farm2home.customer.domain.repository.LocationCityRepository;
import com.farm2home.customer.domain.repository.LocationDistrictRepository;
import com.farm2home.customer.domain.repository.LocationStateRepository;
import com.farm2home.customer.dto.response.LocationCityResponse;
import com.farm2home.customer.dto.response.LocationDistrictResponse;
import com.farm2home.customer.dto.response.LocationStateResponse;
import com.farm2home.customer.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Read-only India location reference data (State -> District -> City). Seeded entirely through
 *  Flyway migrations (see V5__create_india_location_master_data.sql) - there are deliberately no
 *  write endpoints/methods here; master-data changes go through a new migration, not an API. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationServiceImpl {

    private final LocationStateRepository stateRepository;
    private final LocationDistrictRepository districtRepository;
    private final LocationCityRepository cityRepository;

    public List<LocationStateResponse> getStates() {
        return stateRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(s -> LocationStateResponse.builder().id(s.getId()).name(s.getName()).code(s.getCode()).build())
                .toList();
    }

    public List<LocationDistrictResponse> getDistricts(UUID stateId) {
        stateRepository.findByIdAndActiveTrue(stateId)
                .orElseThrow(() -> new ResourceNotFoundException("No state found with id: " + stateId));
        return districtRepository.findAllByStateIdAndActiveTrueOrderByNameAsc(stateId).stream()
                .map(d -> LocationDistrictResponse.builder().id(d.getId()).name(d.getName()).code(d.getCode()).build())
                .toList();
    }

    public List<LocationCityResponse> getCities(UUID districtId) {
        districtRepository.findByIdAndActiveTrue(districtId)
                .orElseThrow(() -> new ResourceNotFoundException("No district found with id: " + districtId));
        return cityRepository.findAllByDistrictIdAndActiveTrueOrderByNameAsc(districtId).stream()
                .map(c -> LocationCityResponse.builder().id(c.getId()).name(c.getName()).build())
                .toList();
    }
}
