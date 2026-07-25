package com.farm2home.farm.service;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.entity.HealthRecord;
import com.farm2home.farm.domain.repository.CowRepository;
import com.farm2home.farm.domain.repository.HealthRecordRepository;
import com.farm2home.farm.dto.request.CreateHealthRecordRequest;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import com.farm2home.farm.service.impl.HealthRecordServiceImpl;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthRecordServiceImplTest {

    @Mock private HealthRecordRepository healthRecordRepository;
    @Mock private CowRepository cowRepository;
    @Mock private FarmMapper mapper;

    @InjectMocks private HealthRecordServiceImpl service;

    private final UUID cowId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();

    private Cow buildCow() {
        return Cow.builder().id(cowId).tagNumber("TAG001").build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("existing cow → creates and links health record")
        void happyPath() {
            CreateHealthRecordRequest req = new CreateHealthRecordRequest();
            Cow cow = buildCow();
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(cow));
            HealthRecord entity = new HealthRecord();
            when(mapper.toEntity(req)).thenReturn(entity);
            HealthRecord saved = HealthRecord.builder().id(recordId).cow(cow).build();
            when(healthRecordRepository.save(entity)).thenReturn(saved);
            when(mapper.toHealthRecordResponse(saved)).thenReturn(HealthRecordResponse.builder().id(recordId).build());

            HealthRecordResponse result = service.create(cowId, req);

            assertThat(entity.getCow()).isEqualTo(cow);
            assertThat(result.getId()).isEqualTo(recordId);
        }

        @Test
        @DisplayName("cow not found → throws ResourceNotFoundException")
        void cowNotFound_throws() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(cowId, new CreateHealthRecordRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByCow()")
    class FindByCow {

        @Test
        @DisplayName("existing cow → returns page")
        void found() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(buildCow()));
            HealthRecord r = HealthRecord.builder().id(recordId).build();
            when(healthRecordRepository.findAllByCowIdAndDeletedFalseOrderByRecordDateDesc(eq(cowId), any()))
                    .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));
            when(mapper.toHealthRecordResponse(r)).thenReturn(HealthRecordResponse.builder().id(recordId).build());

            Page<HealthRecordResponse> result = service.findByCow(cowId, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findLatest()")
    class FindLatest {

        @Test
        @DisplayName("has records → returns most recent")
        void found() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(buildCow()));
            HealthRecord r = HealthRecord.builder().id(recordId).build();
            when(healthRecordRepository.findTopByCowIdAndDeletedFalseOrderByRecordDateDesc(cowId))
                    .thenReturn(Optional.of(r));
            when(mapper.toHealthRecordResponse(r)).thenReturn(HealthRecordResponse.builder().id(recordId).build());

            HealthRecordResponse result = service.findLatest(cowId);

            assertThat(result.getId()).isEqualTo(recordId);
        }

        @Test
        @DisplayName("no records → throws ResourceNotFoundException")
        void noRecords_throws() {
            when(cowRepository.findByIdAndDeletedFalse(cowId)).thenReturn(Optional.of(buildCow()));
            when(healthRecordRepository.findTopByCowIdAndDeletedFalseOrderByRecordDateDesc(cowId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findLatest(cowId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing record → sets deleted=true")
        void softDeletes() {
            HealthRecord r = HealthRecord.builder().id(recordId).deleted(false).build();
            when(healthRecordRepository.findByIdAndCowIdAndDeletedFalse(recordId, cowId)).thenReturn(Optional.of(r));

            service.delete(cowId, recordId);

            assertThat(r.isDeleted()).isTrue();
            verify(healthRecordRepository).save(r);
        }

        @Test
        @DisplayName("missing record → throws ResourceNotFoundException")
        void notFound_throws() {
            when(healthRecordRepository.findByIdAndCowIdAndDeletedFalse(recordId, cowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(cowId, recordId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
