package com.farm2home.customer.service;

import com.farm2home.common.web.exception.ConflictException;
import com.farm2home.customer.domain.entity.LocationCity;
import com.farm2home.customer.domain.entity.LocationDistrict;
import com.farm2home.customer.domain.entity.LocationState;
import com.farm2home.customer.domain.repository.LocationCityRepository;
import com.farm2home.customer.domain.repository.LocationDistrictRepository;
import com.farm2home.customer.domain.repository.LocationStateRepository;
import com.farm2home.customer.dto.request.LocationMasterRequest;
import com.farm2home.customer.dto.response.LocationMasterResponse;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.service.impl.LocationMasterServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationMasterServiceImplTest {

    @Mock private LocationStateRepository states;
    @Mock private LocationDistrictRepository districts;
    @Mock private LocationCityRepository cities;
    @InjectMocks private LocationMasterServiceImpl service;

    private final UUID stateId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();

    private LocationState state(boolean active) {
        return LocationState.builder().id(stateId).name("Andhra Pradesh").code("AP").countryCode("IN").active(active).build();
    }

    private LocationDistrict district(boolean active) {
        return LocationDistrict.builder().id(districtId).stateId(stateId).name("Krishna").code("AP-04").active(active).build();
    }

    private LocationCity city() {
        return LocationCity.builder().id(cityId).districtId(districtId).name("Machilipatnam").active(true).build();
    }

    private LocationMasterRequest stateRequest(String name, String code, boolean active) {
        LocationMasterRequest r = new LocationMasterRequest();
        r.setName(name); r.setCode(code); r.setActive(active);
        return r;
    }

    private LocationMasterRequest districtRequest(String name, String code, UUID forStateId, boolean active) {
        LocationMasterRequest r = new LocationMasterRequest();
        r.setName(name); r.setCode(code); r.setStateId(forStateId); r.setActive(active);
        return r;
    }

    private LocationMasterRequest cityRequest(String name, UUID forDistrictId, boolean active) {
        LocationMasterRequest r = new LocationMasterRequest();
        r.setName(name); r.setDistrictId(forDistrictId); r.setActive(active);
        return r;
    }

    @Nested @DisplayName("States")
    class States {

        @Test
        @DisplayName("states() returns all states sorted by name, filtered by search")
        void list_returnsMatching() {
            LocationState bihar = LocationState.builder().id(UUID.randomUUID()).name("Bihar").code("BR").active(true).build();
            when(states.findAll()).thenReturn(List.of(state(true), bihar));

            List<LocationMasterResponse> result = service.states("andhra");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Andhra Pradesh");
        }

        @Test
        @DisplayName("createState() saves a new state")
        void create_success() {
            when(states.existsByNameIgnoreCase("Andhra Pradesh")).thenReturn(false);
            when(states.existsByCodeIgnoreCase("AP")).thenReturn(false);
            when(states.save(any(LocationState.class))).thenAnswer(inv -> {
                LocationState s = inv.getArgument(0);
                s.setId(stateId);
                return s;
            });

            LocationMasterResponse result = service.createState(stateRequest("Andhra Pradesh", "AP", true));

            assertThat(result.getName()).isEqualTo("Andhra Pradesh");
            assertThat(result.getCode()).isEqualTo("AP");
            assertThat(result.isActive()).isTrue();
            ArgumentCaptor<LocationState> captor = ArgumentCaptor.forClass(LocationState.class);
            verify(states).save(captor.capture());
            assertThat(captor.getValue().getCountryCode()).isEqualTo("IN");
        }

        @Test
        @DisplayName("createState() with a duplicate name → 409 Conflict")
        void create_duplicateName_conflict() {
            when(states.existsByNameIgnoreCase("Andhra Pradesh")).thenReturn(true);

            assertThatThrownBy(() -> service.createState(stateRequest("Andhra Pradesh", "AP2", true)))
                    .isInstanceOf(ConflictException.class);
            verify(states, never()).save(any());
        }

        @Test
        @DisplayName("createState() with a duplicate code → 409 Conflict")
        void create_duplicateCode_conflict() {
            when(states.existsByNameIgnoreCase("New State")).thenReturn(false);
            when(states.existsByCodeIgnoreCase("AP")).thenReturn(true);

            assertThatThrownBy(() -> service.createState(stateRequest("New State", "AP", true)))
                    .isInstanceOf(ConflictException.class);
            verify(states, never()).save(any());
        }

        @Test
        @DisplayName("updateState() renames and re-codes a state")
        void update_success() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));
            when(states.existsByNameIgnoreCase("Andhra Pradesh Renamed")).thenReturn(false);
            when(states.existsByCodeIgnoreCase("AZ")).thenReturn(false);
            when(states.save(any(LocationState.class))).thenAnswer(inv -> inv.getArgument(0));

            LocationMasterResponse result = service.updateState(stateId, stateRequest("Andhra Pradesh Renamed", "AZ", true));

            assertThat(result.getName()).isEqualTo("Andhra Pradesh Renamed");
            assertThat(result.getCode()).isEqualTo("AZ");
        }

        @Test
        @DisplayName("updateState() deactivating a state (active=false) is allowed")
        void update_deactivate() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));
            when(states.save(any(LocationState.class))).thenAnswer(inv -> inv.getArgument(0));

            LocationMasterResponse result = service.updateState(stateId, stateRequest("Andhra Pradesh", "AP", false));

            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("updateState() reactivating a state (active=true) is allowed")
        void update_reactivate() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(false)));
            when(states.save(any(LocationState.class))).thenAnswer(inv -> inv.getArgument(0));

            LocationMasterResponse result = service.updateState(stateId, stateRequest("Andhra Pradesh", "AP", true));

            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("updateState() renaming to another state's existing name → 409 Conflict")
        void update_renameToDuplicate_conflict() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));
            when(states.existsByNameIgnoreCase("Bihar")).thenReturn(true);

            assertThatThrownBy(() -> service.updateState(stateId, stateRequest("Bihar", "AP", true)))
                    .isInstanceOf(ConflictException.class);
            verify(states, never()).save(any());
        }

        @Test
        @DisplayName("updateState() unknown id → 404 Not Found")
        void update_unknownId_notFound() {
            UUID badId = UUID.randomUUID();
            when(states.findById(badId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateState(badId, stateRequest("X", "Y", true)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deleteState() with no active districts succeeds")
        void delete_noActiveDistricts_success() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));
            when(districts.existsByStateIdAndActiveTrue(stateId)).thenReturn(false);

            service.deleteState(stateId);

            verify(states).delete(any(LocationState.class));
        }

        @Test
        @DisplayName("deleteState() with active districts → 409 Conflict, not deleted")
        void delete_withActiveDistricts_conflict() {
            when(districts.existsByStateIdAndActiveTrue(stateId)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteState(stateId))
                    .isInstanceOf(ConflictException.class);
            verify(states, never()).delete(any());
        }
    }

    @Nested @DisplayName("Districts")
    class Districts {

        @Test
        @DisplayName("districts() scoped to a state returns only that state's districts")
        void list_scopedToState() {
            when(districts.findAllByStateIdOrderByNameAsc(stateId)).thenReturn(List.of(district(true)));
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));

            List<LocationMasterResponse> result = service.districts(stateId, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStateName()).isEqualTo("Andhra Pradesh");
            verify(districts, never()).findAll();
        }

        @Test
        @DisplayName("createDistrict() under an active state succeeds")
        void create_success() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));
            when(districts.existsByStateIdAndNameIgnoreCase(stateId, "Krishna")).thenReturn(false);
            when(districts.existsByCodeIgnoreCase("AP-04")).thenReturn(false);
            when(districts.save(any(LocationDistrict.class))).thenAnswer(inv -> {
                LocationDistrict d = inv.getArgument(0);
                d.setId(districtId);
                return d;
            });

            LocationMasterResponse result = service.createDistrict(districtRequest("Krishna", "AP-04", stateId, true));

            assertThat(result.getName()).isEqualTo("Krishna");
            assertThat(result.getStateId()).isEqualTo(stateId);
        }

        @Test
        @DisplayName("createDistrict() with a duplicate name within the same state → 409 Conflict")
        void create_duplicateNameInState_conflict() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));
            when(districts.existsByStateIdAndNameIgnoreCase(stateId, "Krishna")).thenReturn(true);

            assertThatThrownBy(() -> service.createDistrict(districtRequest("Krishna", "AP-99", stateId, true)))
                    .isInstanceOf(ConflictException.class);
            verify(districts, never()).save(any());
        }

        @Test
        @DisplayName("createDistrict() under an inactive state → 409 Conflict")
        void create_inactiveState_conflict() {
            when(states.findById(stateId)).thenReturn(Optional.of(state(false)));

            assertThatThrownBy(() -> service.createDistrict(districtRequest("Krishna", "AP-04", stateId, true)))
                    .isInstanceOf(ConflictException.class);
            verify(districts, never()).existsByStateIdAndNameIgnoreCase(any(), any());
            verify(districts, never()).save(any());
        }

        @Test
        @DisplayName("createDistrict() referencing an unknown state → 404 Not Found")
        void create_unknownState_notFound() {
            UUID badStateId = UUID.randomUUID();
            when(states.findById(badStateId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createDistrict(districtRequest("Krishna", "AP-04", badStateId, true)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deleteDistrict() with no active cities succeeds")
        void delete_noActiveCities_success() {
            when(districts.findById(districtId)).thenReturn(Optional.of(district(true)));
            when(cities.existsByDistrictIdAndActiveTrue(districtId)).thenReturn(false);

            service.deleteDistrict(districtId);

            verify(districts).delete(any(LocationDistrict.class));
        }

        @Test
        @DisplayName("deleteDistrict() with active cities → 409 Conflict, not deleted")
        void delete_withActiveCities_conflict() {
            when(cities.existsByDistrictIdAndActiveTrue(districtId)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteDistrict(districtId))
                    .isInstanceOf(ConflictException.class);
            verify(districts, never()).delete(any());
        }
    }

    @Nested @DisplayName("Cities")
    class Cities {

        @Test
        @DisplayName("cities() scoped to a district returns only that district's cities")
        void list_scopedToDistrict() {
            when(cities.findAll()).thenReturn(List.of(city()));
            when(districts.findById(districtId)).thenReturn(Optional.of(district(true)));
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));

            List<LocationMasterResponse> result = service.cities(null, districtId, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDistrictName()).isEqualTo("Krishna");
        }

        @Test
        @DisplayName("createCity() under an active district succeeds")
        void create_success() {
            when(districts.findById(districtId)).thenReturn(Optional.of(district(true)));
            when(cities.existsByDistrictIdAndNameIgnoreCase(districtId, "Machilipatnam")).thenReturn(false);
            when(cities.save(any(LocationCity.class))).thenAnswer(inv -> {
                LocationCity c = inv.getArgument(0);
                c.setId(cityId);
                return c;
            });
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));

            LocationMasterResponse result = service.createCity(cityRequest("Machilipatnam", districtId, true));

            assertThat(result.getName()).isEqualTo("Machilipatnam");
            assertThat(result.getDistrictId()).isEqualTo(districtId);
        }

        @Test
        @DisplayName("createCity() with a duplicate name within the same district → 409 Conflict")
        void create_duplicateName_conflict() {
            when(districts.findById(districtId)).thenReturn(Optional.of(district(true)));
            when(cities.existsByDistrictIdAndNameIgnoreCase(districtId, "Machilipatnam")).thenReturn(true);

            assertThatThrownBy(() -> service.createCity(cityRequest("Machilipatnam", districtId, true)))
                    .isInstanceOf(ConflictException.class);
            verify(cities, never()).save(any());
        }

        @Test
        @DisplayName("createCity() under an inactive district → 409 Conflict")
        void create_inactiveDistrict_conflict() {
            when(districts.findById(districtId)).thenReturn(Optional.of(district(false)));

            assertThatThrownBy(() -> service.createCity(cityRequest("Machilipatnam", districtId, true)))
                    .isInstanceOf(ConflictException.class);
            verify(cities, never()).existsByDistrictIdAndNameIgnoreCase(any(), any());
            verify(cities, never()).save(any());
        }

        @Test
        @DisplayName("createCity() referencing an unknown district → 404 Not Found")
        void create_unknownDistrict_notFound() {
            UUID badDistrictId = UUID.randomUUID();
            when(districts.findById(badDistrictId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createCity(cityRequest("Machilipatnam", badDistrictId, true)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("updateCity() renames and toggles active")
        void update_success() {
            when(cities.findById(cityId)).thenReturn(Optional.of(city()));
            when(districts.findById(districtId)).thenReturn(Optional.of(district(true)));
            when(cities.existsByDistrictIdAndNameIgnoreCaseAndIdNot(eq(districtId), eq("Machilipatnam Renamed"), eq(cityId))).thenReturn(false);
            when(cities.save(any(LocationCity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(states.findById(stateId)).thenReturn(Optional.of(state(true)));

            LocationMasterResponse result = service.updateCity(cityId, cityRequest("Machilipatnam Renamed", districtId, false));

            assertThat(result.getName()).isEqualTo("Machilipatnam Renamed");
            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("deleteCity() succeeds unconditionally (cities have no dependents)")
        void delete_success() {
            when(cities.findById(cityId)).thenReturn(Optional.of(city()));

            service.deleteCity(cityId);

            verify(cities).delete(any(LocationCity.class));
        }
    }
}
