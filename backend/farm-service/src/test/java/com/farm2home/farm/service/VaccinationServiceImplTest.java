package com.farm2home.farm.service;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.entity.Vaccination;
import com.farm2home.farm.domain.repository.CowRepository;
import com.farm2home.farm.domain.repository.VaccinationRepository;
import com.farm2home.farm.dto.request.CreateVaccinationRequest;
import com.farm2home.farm.dto.response.VaccinationResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import com.farm2home.farm.service.impl.VaccinationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaccinationServiceImplTest {

    @Mock private VaccinationRepository vaccinationRepository;
    @Mock private CowRepository cowRepository;
    @Mock private FarmMapper mapper;

    @InjectMocks private VaccinationServiceImpl service;

    private final UUID cowId = UUID.randomUUID();
    private final UUID vaccinationId = UUID.randomUUID();

    private Cow buildCow() {
        return Cow.builder().id(cowId).tagNumber("TAG001").build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("existing cow → creates and links vaccination")
        void happyPath() {
            CreateVaccinationRequest req = new CreateVaccinationRequest();
            req.setVaccineName("FMD");
            req.setAdministeredAt(LocalDate.now());

            Cow cow = buildCow();
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));
            Vaccination entity = new Vaccination();
            when(mapper.toEntity(req)).thenReturn(entity);
            Vaccination saved = Vaccination.builder().id(vaccinationId).cow(cow).vaccineName("FMD").build();
            when(vaccinationRepository.save(entity)).thenReturn(saved);
            when(mapper.toVaccinationResponse(saved)).thenReturn(
                    VaccinationResponse.builder().id(vaccinationId).build());

            VaccinationResponse result = service.create(cowId, req);

            assertThat(entity.getCow()).isEqualTo(cow);
            assertThat(result.getId()).isEqualTo(vaccinationId);
        }

        @Test
        @DisplayName("cow not found → throws ResourceNotFoundException")
        void cowNotFound_throws() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(cowId, new CreateVaccinationRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(vaccinationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findByCow()")
    class FindByCow {

        @Test
        @DisplayName("existing cow → returns page of vaccinations")
        void found() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(buildCow()));
            Vaccination v = Vaccination.builder().id(vaccinationId).build();
            Page<Vaccination> page = new PageImpl<>(List.of(v), PageRequest.of(0, 20), 1);
            when(vaccinationRepository.findAllByCowIdAndDeletedFalseOrderByAdministeredAtDesc(eq(cowId), any()))
                    .thenReturn(page);
            when(mapper.toVaccinationResponse(v)).thenReturn(VaccinationResponse.builder().id(vaccinationId).build());

            Page<VaccinationResponse> result = service.findByCow(cowId, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findUpcoming()")
    class FindUpcoming {

        @Test
        @DisplayName("delegates to repository with correct date window")
        void delegates() {
            Vaccination v = Vaccination.builder().id(vaccinationId).build();
            when(vaccinationRepository.findUpcomingVaccinations(any(), any())).thenReturn(List.of(v));
            when(mapper.toVaccinationResponse(v)).thenReturn(VaccinationResponse.builder().id(vaccinationId).build());

            List<VaccinationResponse> result = service.findUpcoming(7);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing vaccination → sets deleted=true")
        void softDeletes() {
            Vaccination v = Vaccination.builder().id(vaccinationId).deleted(false).build();
            when(vaccinationRepository.findByIdAndCowIdAndDeletedFalse(vaccinationId, cowId))
                    .thenReturn(Optional.of(v));

            service.delete(cowId, vaccinationId);

            assertThat(v.isDeleted()).isTrue();
            verify(vaccinationRepository).save(v);
        }

        @Test
        @DisplayName("missing vaccination → throws ResourceNotFoundException")
        void notFound_throws() {
            when(vaccinationRepository.findByIdAndCowIdAndDeletedFalse(vaccinationId, cowId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(cowId, vaccinationId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
