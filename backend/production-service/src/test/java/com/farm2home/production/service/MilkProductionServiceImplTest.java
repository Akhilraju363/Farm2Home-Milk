package com.farm2home.production.service;

import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.domain.enums.MilkSession;
import com.farm2home.production.domain.enums.QualityGrade;
import com.farm2home.production.domain.repository.MilkProductionRepository;
import com.farm2home.production.dto.request.CreateMilkProductionRequest;
import com.farm2home.production.dto.request.UpdateMilkProductionRequest;
import com.farm2home.production.dto.response.MilkProductionResponse;
import com.farm2home.production.exception.ProductionException;
import com.farm2home.production.exception.ResourceNotFoundException;
import com.farm2home.production.mapper.ProductionMapper;
import com.farm2home.production.service.impl.MilkProductionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MilkProductionServiceImplTest {

    @Mock private MilkProductionRepository repository;
    @Mock private ProductionMapper mapper;

    @InjectMocks private MilkProductionServiceImpl service;

    private final UUID recordId = UUID.randomUUID();
    private final UUID cowId    = UUID.randomUUID();

    private MilkProduction buildRecord() {
        return MilkProduction.builder()
                .id(recordId)
                .cowId(cowId)
                .collectionDate(LocalDate.now())
                .session(MilkSession.MORNING)
                .quantityLiters(new BigDecimal("10.50"))
                .qualityGrade(QualityGrade.A)
                .deleted(false)
                .build();
    }

    private MilkProductionResponse buildResponse() {
        return MilkProductionResponse.builder()
                .id(recordId).cowId(cowId)
                .session("MORNING").qualityGrade("A")
                .quantityLiters(new BigDecimal("10.50")).build();
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("new record → saves and returns response")
        void happyPath() {
            when(repository.existsByCowIdAndCollectionDateAndSessionAndDeletedFalse(
                    any(), any(), any())).thenReturn(false);
            when(mapper.toEntity(any(CreateMilkProductionRequest.class))).thenReturn(new MilkProduction());
            MilkProduction saved = buildRecord();
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(buildResponse());

            CreateMilkProductionRequest req = new CreateMilkProductionRequest();
            req.setCowId(cowId);
            req.setCollectionDate(LocalDate.now());
            req.setSession(MilkSession.MORNING);
            req.setQuantityLiters(new BigDecimal("10.50"));

            MilkProductionResponse result = service.create(req);

            assertThat(result.getSession()).isEqualTo("MORNING");
            verify(repository).save(any(MilkProduction.class));
        }

        @Test
        @DisplayName("duplicate cow+date+session → throws ProductionException")
        void duplicate_throws() {
            when(repository.existsByCowIdAndCollectionDateAndSessionAndDeletedFalse(
                    any(), any(), any())).thenReturn(true);

            CreateMilkProductionRequest req = new CreateMilkProductionRequest();
            req.setCowId(cowId);
            req.setCollectionDate(LocalDate.now());
            req.setSession(MilkSession.MORNING);
            req.setQuantityLiters(new BigDecimal("10.50"));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(ProductionException.class)
                    .hasMessageContaining("Production record already exists");
            verify(repository, never()).save(any());
        }
    }

    // ── FindAll / FindByCow / Summaries ─────────────────────────────────────────

    @Nested @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns mapped page")
        void returnsMappedPage() {
            MilkProduction record = buildRecord();
            when(repository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
            when(mapper.toResponse(record)).thenReturn(buildResponse());

            assertThat(service.findAll(org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested @DisplayName("findByCow()")
    class FindByCow {

        @Test
        @DisplayName("returns mapped page for the given cow")
        void returnsMappedPage() {
            MilkProduction record = buildRecord();
            when(repository.findAllByCowIdAndDeletedFalse(eq(cowId), any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
            when(mapper.toResponse(record)).thenReturn(buildResponse());

            assertThat(service.findByCow(cowId, org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested @DisplayName("getDailySummaryByCow() / getDailySummary()")
    class Summaries {

        @Test
        @DisplayName("getDailySummaryByCow delegates to repository with the given range")
        void byCow_delegates() {
            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now();
            when(repository.findDailySummaryByCow(cowId, from, to)).thenReturn(java.util.List.of());

            service.getDailySummaryByCow(cowId, from, to);

            verify(repository).findDailySummaryByCow(cowId, from, to);
        }

        @Test
        @DisplayName("getDailySummary delegates to repository with the given range")
        void overall_delegates() {
            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now();
            when(repository.findDailySummary(from, to)).thenReturn(java.util.List.of());

            service.getDailySummary(from, to);

            verify(repository).findDailySummary(from, to);
        }
    }

    // ── FindById ─────────────────────────────────────────────────────────────────

    @Nested @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing record → returns response")
        void found() {
            MilkProduction record = buildRecord();
            when(repository.findByIdAndDeletedFalse(recordId)).thenReturn(Optional.of(record));
            when(mapper.toResponse(record)).thenReturn(buildResponse());

            MilkProductionResponse result = service.findById(recordId);
            assertThat(result.getId()).isEqualTo(recordId);
        }

        @Test
        @DisplayName("non-existent record → throws ResourceNotFoundException")
        void notFound() {
            when(repository.findByIdAndDeletedFalse(recordId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(recordId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("partial update → only non-null fields are changed")
        void partialUpdate() {
            MilkProduction record = buildRecord();
            when(repository.findByIdAndDeletedFalse(recordId)).thenReturn(Optional.of(record));
            when(repository.save(record)).thenReturn(record);
            when(mapper.toResponse(record)).thenReturn(buildResponse());

            UpdateMilkProductionRequest req = new UpdateMilkProductionRequest();
            req.setQuantityLiters(new BigDecimal("12.00"));

            doAnswer(inv -> {
                UpdateMilkProductionRequest r = inv.getArgument(0);
                MilkProduction target = inv.getArgument(1);
                if (r.getQuantityLiters() != null) target.setQuantityLiters(r.getQuantityLiters());
                if (r.getFatPercentage() != null) target.setFatPercentage(r.getFatPercentage());
                if (r.getSnfPercentage() != null) target.setSnfPercentage(r.getSnfPercentage());
                if (r.getQualityGrade() != null) target.setQualityGrade(r.getQualityGrade());
                if (r.getCollectedBy() != null) target.setCollectedBy(r.getCollectedBy());
                if (r.getNotes() != null) target.setNotes(r.getNotes());
                return null;
            }).when(mapper).updateEntityFromRequest(eq(req), eq(record));

            service.update(recordId, req);

            assertThat(record.getQuantityLiters()).isEqualByComparingTo("12.00");
            assertThat(record.getSession()).isEqualTo(MilkSession.MORNING); // unchanged
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing record → sets deleted=true")
        void softDeletes() {
            MilkProduction record = buildRecord();
            when(repository.findByIdAndDeletedFalse(recordId)).thenReturn(Optional.of(record));

            service.delete(recordId);

            assertThat(record.isDeleted()).isTrue();
            verify(repository).save(record);
        }

        @Test
        @DisplayName("non-existent record → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(recordId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(recordId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
