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
