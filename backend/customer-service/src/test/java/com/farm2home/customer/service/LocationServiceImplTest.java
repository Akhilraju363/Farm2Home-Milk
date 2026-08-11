package com.farm2home.customer.service;

import com.farm2home.customer.domain.entity.LocationCity;
import com.farm2home.customer.domain.entity.LocationDistrict;
import com.farm2home.customer.domain.entity.LocationState;
import com.farm2home.customer.domain.repository.LocationCityRepository;
import com.farm2home.customer.domain.repository.LocationDistrictRepository;
import com.farm2home.customer.domain.repository.LocationStateRepository;
import com.farm2home.customer.dto.response.LocationCityResponse;
import com.farm2home.customer.dto.response.LocationDistrictResponse;
import com.farm2home.customer.dto.response.LocationStateResponse;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.service.impl.LocationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    @Mock private LocationStateRepository stateRepository;
    @Mock private LocationDistrictRepository districtRepository;
    @Mock private LocationCityRepository cityRepository;
    @InjectMocks private LocationServiceImpl locationService;

    private final UUID stateId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();

    private LocationState state() {
        return LocationState.builder().id(stateId).name("Andhra Pradesh").code("AP").active(true).build();
    }

    private LocationDistrict district() {
        return LocationDistrict.builder().id(districtId).stateId(stateId).name("Krishna").code("AP-04").active(true).build();
    }

    @Test
    @DisplayName("getStates returns every active state, alphabetically ordered by the repository query")
    void getStates_returnsAllActiveStates() {
        when(stateRepository.findAllByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(state(), LocationState.builder().id(UUID.randomUUID()).name("Bihar").code("BR").active(true).build()));

        List<LocationStateResponse> result = locationService.getStates();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("AP");
    }

    @Test
    @DisplayName("getDistricts returns districts scoped to the given state")
    void getDistricts_validState_returnsScopedDistricts() {
        when(stateRepository.findByIdAndActiveTrue(stateId)).thenReturn(Optional.of(state()));
        when(districtRepository.findAllByStateIdAndActiveTrueOrderByNameAsc(stateId)).thenReturn(List.of(district()));

        List<LocationDistrictResponse> result = locationService.getDistricts(stateId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Krishna");
    }

    @Test
    @DisplayName("getDistricts on an unknown/invalid state id throws 404 without querying districts")
    void getDistricts_invalidState_throwsNotFound() {
        UUID badStateId = UUID.randomUUID();
        when(stateRepository.findByIdAndActiveTrue(badStateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getDistricts(badStateId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(districtRepository);
    }

    @Test
    @DisplayName("getCities returns cities scoped to the given district")
    void getCities_validDistrict_returnsScopedCities() {
        when(districtRepository.findByIdAndActiveTrue(districtId)).thenReturn(Optional.of(district()));
        when(cityRepository.findAllByDistrictIdAndActiveTrueOrderByNameAsc(districtId))
                .thenReturn(List.of(LocationCity.builder().id(UUID.randomUUID()).districtId(districtId).name("Machilipatnam").active(true).build()));

        List<LocationCityResponse> result = locationService.getCities(districtId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Machilipatnam");
    }

    @Test
    @DisplayName("getCities on an unknown/invalid district id throws 404 without querying cities")
    void getCities_invalidDistrict_throwsNotFound() {
        UUID badDistrictId = UUID.randomUUID();
        when(districtRepository.findByIdAndActiveTrue(badDistrictId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getCities(badDistrictId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cityRepository);
    }

    @Test
    @DisplayName("a district belonging to a different state is never returned - enforced by the repository query being scoped by state_id")
    void getDistricts_neverReturnsDistrictsFromAnotherState() {
        UUID otherStateId = UUID.randomUUID();
        when(stateRepository.findByIdAndActiveTrue(stateId)).thenReturn(Optional.of(state()));
        // The repository call itself is scoped to `stateId` - a district row with a different
        // state_id would never be returned by findAllByStateIdAndActiveTrueOrderByNameAsc(stateId)
        // in the real query; here we assert the service only ever asks for the requested state's id.
        when(districtRepository.findAllByStateIdAndActiveTrueOrderByNameAsc(stateId)).thenReturn(List.of(district()));

        locationService.getDistricts(stateId);

        org.mockito.Mockito.verify(districtRepository).findAllByStateIdAndActiveTrueOrderByNameAsc(stateId);
        org.mockito.Mockito.verify(districtRepository, org.mockito.Mockito.never())
                .findAllByStateIdAndActiveTrueOrderByNameAsc(otherStateId);
    }
}
