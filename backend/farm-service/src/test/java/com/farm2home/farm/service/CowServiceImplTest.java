package com.farm2home.farm.service;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.enums.CowStatus;
import com.farm2home.farm.domain.repository.CowRepository;
import com.farm2home.farm.dto.request.CreateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowStatusRequest;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.exception.FarmException;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import com.farm2home.farm.service.impl.CowServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CowServiceImplTest {

    @Mock private CowRepository cowRepository;
    @Mock private FarmMapper mapper;

    @InjectMocks private CowServiceImpl service;

    private final UUID cowId = UUID.randomUUID();

    private Cow buildCow(CowStatus status) {
        return Cow.builder()
                .id(cowId)
                .tagNumber("TAG001")
                .cowName("Ganga")
                .breed("Holstein")
                .status(status)
                .deleted(false)
                .build();
    }

    private CowResponse buildResponse(CowStatus status) {
        return CowResponse.builder().id(cowId).tagNumber("TAG001").status(status.name()).build();
    }

    /** Mirrors FarmMapper's real (generated) toEntity() behavior, since mapper is mocked here. */
    private static Cow realToEntity(org.mockito.invocation.InvocationOnMock inv) {
        CreateCowRequest r = inv.getArgument(0);
        Cow c = new Cow();
        c.setTagNumber(r.getTagNumber().toUpperCase());
        c.setCowName(r.getCowName());
        c.setBreed(r.getBreed());
        c.setDateOfBirth(r.getDateOfBirth());
        c.setPurchaseDate(r.getPurchaseDate());
        return c;
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("new tag number → saves and returns response")
        void happyPath() {
            when(cowRepository.existsByTagNumberAndDeletedFalse("TAG001")).thenReturn(false);
            when(mapper.toEntity(any(CreateCowRequest.class))).thenAnswer(CowServiceImplTest::realToEntity);
            Cow saved = buildCow(CowStatus.ACTIVE);
            when(cowRepository.save(any())).thenReturn(saved);
            when(mapper.toCowResponse(saved)).thenReturn(buildResponse(CowStatus.ACTIVE));

            CreateCowRequest req = new CreateCowRequest();
            req.setTagNumber("TAG001");
            req.setBreed("Holstein");

            CowResponse result = service.create(req);

            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            verify(cowRepository).save(any(Cow.class));
        }

        @Test
        @DisplayName("duplicate tag number → throws FarmException")
        void duplicateTag_throws() {
            when(cowRepository.existsByTagNumberAndDeletedFalse("TAG001")).thenReturn(true);

            CreateCowRequest req = new CreateCowRequest();
            req.setTagNumber("TAG001");
            req.setBreed("Holstein");

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(FarmException.class)
                    .hasMessageContaining("Tag number already registered");
            verify(cowRepository, never()).save(any());
        }

        @Test
        @DisplayName("tag number is uppercased before saving")
        void tagNumberUppercased() {
            // existsByTagNumberAndDeletedFalse is checked against the raw request value -
            // only the entity's own tagNumber field gets uppercased.
            when(cowRepository.existsByTagNumberAndDeletedFalse("tag002")).thenReturn(false);
            when(mapper.toEntity(any(CreateCowRequest.class))).thenAnswer(CowServiceImplTest::realToEntity);
            when(cowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toCowResponse(any())).thenReturn(buildResponse(CowStatus.ACTIVE));

            CreateCowRequest req = new CreateCowRequest();
            req.setTagNumber("tag002");
            req.setBreed("Gir");

            service.create(req);

            verify(cowRepository).save(argThat(c -> "TAG002".equals(c.getTagNumber())));
        }
    }

    // ── FindById ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing cow → returns response")
        void found() {
            Cow cow = buildCow(CowStatus.ACTIVE);
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));
            when(mapper.toCowResponse(cow)).thenReturn(buildResponse(CowStatus.ACTIVE));

            CowResponse result = service.findById(cowId);
            assertThat(result.getId()).isEqualTo(cowId);
        }

        @Test
        @DisplayName("non-existent cow → throws ResourceNotFoundException")
        void notFound() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(cowId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── FindAll ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("farmId + status both provided → uses the combined query")
        void farmIdAndStatus() {
            UUID farmId = UUID.randomUUID();
            Cow cow = buildCow(CowStatus.ACTIVE);
            var page = new org.springframework.data.domain.PageImpl<>(List.of(cow));
            when(cowRepository.findAllByFarmIdAndStatusAndDeletedFalse(eq(farmId), eq(CowStatus.ACTIVE), any()))
                    .thenReturn(page);
            when(mapper.toCowResponse(cow)).thenReturn(buildResponse(CowStatus.ACTIVE));

            var result = service.findAll(CowStatus.ACTIVE, farmId, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(cowRepository, never()).findAllByDeletedFalse(any());
        }

        @Test
        @DisplayName("only farmId provided → filters by farm")
        void farmIdOnly() {
            UUID farmId = UUID.randomUUID();
            Cow cow = buildCow(CowStatus.ACTIVE);
            var page = new org.springframework.data.domain.PageImpl<>(List.of(cow));
            when(cowRepository.findAllByFarmIdAndDeletedFalse(eq(farmId), any())).thenReturn(page);
            when(mapper.toCowResponse(cow)).thenReturn(buildResponse(CowStatus.ACTIVE));

            var result = service.findAll(null, farmId, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("no filters → returns all non-deleted cows")
        void noFilters() {
            Cow cow = buildCow(CowStatus.ACTIVE);
            var page = new org.springframework.data.domain.PageImpl<>(List.of(cow));
            when(cowRepository.findAllByDeletedFalse(any())).thenReturn(page);
            when(mapper.toCowResponse(cow)).thenReturn(buildResponse(CowStatus.ACTIVE));

            var result = service.findAll(null, null, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ── FindIdsByFarm ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findIdsByFarm()")
    class FindIdsByFarm {

        @Test
        @DisplayName("delegates to the id-only repository projection")
        void delegates() {
            UUID farmId = UUID.randomUUID();
            when(cowRepository.findIdsByFarmIdAndDeletedFalse(farmId)).thenReturn(List.of(cowId));

            List<UUID> result = service.findIdsByFarm(farmId);

            assertThat(result).containsExactly(cowId);
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("partial update → only non-null fields are changed")
        void partialUpdate() {
            Cow cow = buildCow(CowStatus.ACTIVE);
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));
            when(cowRepository.save(cow)).thenReturn(cow);
            when(mapper.toCowResponse(cow)).thenReturn(buildResponse(CowStatus.ACTIVE));

            UpdateCowRequest req = new UpdateCowRequest();
            req.setBreed("Jersey");   // update only breed

            doAnswer(inv -> {
                UpdateCowRequest r = inv.getArgument(0);
                Cow target = inv.getArgument(1);
                if (org.springframework.util.StringUtils.hasText(r.getCowName())) target.setCowName(r.getCowName());
                if (org.springframework.util.StringUtils.hasText(r.getBreed())) target.setBreed(r.getBreed());
                if (r.getDateOfBirth() != null) target.setDateOfBirth(r.getDateOfBirth());
                if (r.getPurchaseDate() != null) target.setPurchaseDate(r.getPurchaseDate());
                return null;
            }).when(mapper).updateCowFromRequest(eq(req), eq(cow));

            service.update(cowId, req);

            assertThat(cow.getBreed()).isEqualTo("Jersey");
            assertThat(cow.getCowName()).isEqualTo("Ganga"); // unchanged
        }
    }

    // ── UpdateStatus ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("ACTIVE → SICK updates status field")
        void activeToSick() {
            Cow cow = buildCow(CowStatus.ACTIVE);
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));
            when(cowRepository.save(cow)).thenReturn(cow);
            when(mapper.toCowResponse(cow)).thenReturn(buildResponse(CowStatus.SICK));

            UpdateCowStatusRequest req = new UpdateCowStatusRequest();
            req.setStatus(CowStatus.SICK);

            service.updateStatus(cowId, req);

            assertThat(cow.getStatus()).isEqualTo(CowStatus.SICK);
        }

        @Test
        @DisplayName("SOLD → ACTIVE throws FarmException")
        void soldToActive_throws() {
            Cow cow = buildCow(CowStatus.SOLD);
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));

            UpdateCowStatusRequest req = new UpdateCowStatusRequest();
            req.setStatus(CowStatus.ACTIVE);

            assertThatThrownBy(() -> service.updateStatus(cowId, req))
                    .isInstanceOf(FarmException.class)
                    .hasMessageContaining("Cannot transition cow from SOLD to ACTIVE");
            verify(cowRepository, never()).save(any());
        }

        @Test
        @DisplayName("DECEASED → SICK throws FarmException")
        void deceasedToSick_throws() {
            Cow cow = buildCow(CowStatus.DECEASED);
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));

            UpdateCowStatusRequest req = new UpdateCowStatusRequest();
            req.setStatus(CowStatus.SICK);

            assertThatThrownBy(() -> service.updateStatus(cowId, req))
                    .isInstanceOf(FarmException.class)
                    .hasMessageContaining("Cannot transition cow from DECEASED to SICK");
            verify(cowRepository, never()).save(any());
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing cow → sets deleted=true")
        void softDeletes() {
            Cow cow = buildCow(CowStatus.ACTIVE);
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));

            service.delete(cowId);

            assertThat(cow.isDeleted()).isTrue();
            verify(cowRepository).save(cow);
        }

        @Test
        @DisplayName("non-existent cow → throws ResourceNotFoundException")
        void notFound_throws() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(cowId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
