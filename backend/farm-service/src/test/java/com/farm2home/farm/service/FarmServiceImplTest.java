package com.farm2home.farm.service;

import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.farm.domain.entity.Farm;
import com.farm2home.farm.domain.repository.FarmRepository;
import com.farm2home.farm.dto.request.CreateFarmRequest;
import com.farm2home.farm.dto.request.UpdateFarmRequest;
import com.farm2home.farm.dto.response.FarmResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import com.farm2home.farm.service.impl.FarmServiceImpl;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmServiceImplTest {

    @Mock private FarmRepository repository;
    @Mock private FarmMapper mapper;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private FarmServiceImpl service;

    private final UUID farmId = UUID.randomUUID();

    private Farm buildFarm() {
        return Farm.builder().id(farmId).farmName("Green Meadows").ownerName("Ravi").deleted(false).build();
    }

    private FarmResponse buildResponse() {
        return FarmResponse.builder().id(farmId).farmName("Green Meadows").ownerName("Ravi").build();
    }

    @Nested @DisplayName("create()")
    class Create {
        @Test
        @DisplayName("valid request → saves farm")
        void happyPath() {
            CreateFarmRequest req = new CreateFarmRequest();
            req.setFarmName("Green Meadows");
            req.setOwnerName("Ravi");
            Farm entity = buildFarm();
            when(mapper.toEntity(req)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toFarmResponse(entity)).thenReturn(buildResponse());

            FarmResponse result = service.create(req);

            assertThat(result.getFarmName()).isEqualTo("Green Meadows");
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("findAll()")
    class FindAll {
        @Test
        @DisplayName("returns page of farms")
        void happyPath() {
            when(repository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(buildFarm())));
            when(mapper.toFarmResponse(any())).thenReturn(buildResponse());

            assertThat(service.findAll(org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested @DisplayName("findById()")
    class FindById {
        @Test
        @DisplayName("existing farm → returns response")
        void found() {
            when(repository.findByIdAndDeletedFalse(farmId)).thenReturn(Optional.of(buildFarm()));
            when(mapper.toFarmResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(farmId).getId()).isEqualTo(farmId);
        }

        @Test
        @DisplayName("missing farm → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(farmId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(farmId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("update()")
    class Update {
        @Test
        @DisplayName("existing farm → applies changes and saves")
        void happyPath() {
            Farm entity = buildFarm();
            UpdateFarmRequest req = new UpdateFarmRequest();
            req.setLocation("Nashik");
            when(repository.findByIdAndDeletedFalse(farmId)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toFarmResponse(entity)).thenReturn(buildResponse());

            service.update(farmId, req);

            verify(mapper).updateFarmFromRequest(req, entity);
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {
        @Test
        @DisplayName("existing farm → sets deleted=true")
        void softDeletes() {
            Farm entity = buildFarm();
            when(repository.findByIdAndDeletedFalse(farmId)).thenReturn(Optional.of(entity));

            service.delete(farmId);

            assertThat(entity.isDeleted()).isTrue();
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("uploadImage()")
    class UploadImage {
        @Test
        @DisplayName("valid file → stores and sets imageUrl")
        void happyPath() {
            Farm entity = buildFarm();
            MockMultipartFile file = new MockMultipartFile("file", "farm.jpg", "image/jpeg", new byte[]{1, 2, 3});
            when(repository.findByIdAndDeletedFalse(farmId)).thenReturn(Optional.of(entity));
            when(fileStorageService.store(file, "farms")).thenReturn("farms/uuid.jpg");
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toFarmResponse(entity)).thenReturn(buildResponse());

            service.uploadImage(farmId, file);

            assertThat(entity.getImageUrl()).isEqualTo("/uploads/farms/uuid.jpg");
            verify(repository).save(entity);
        }
    }

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("keyword + date range combine into one query")
        void allFiltersCombine() {
            Farm farm = buildFarm();
            var page = new PageImpl<>(List.of(farm), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toFarmResponse(farm)).thenReturn(buildResponse());

            Page<FarmResponse> result = service.search(
                    "meadows", LocalDate.now().minusDays(7), LocalDate.now(), PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(farmId);
        }

        @Test
        @DisplayName("blank keyword → keyword predicate is not applied")
        void blankKeyword_notApplied() {
            var page = new PageImpl<Farm>(List.of(), PageRequest.of(0, 20), 0);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            Page<FarmResponse> result = service.search("   ", null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("no filters → returns all non-deleted farms")
        void noFilters() {
            Farm farm = buildFarm();
            var page = new PageImpl<>(List.of(farm), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toFarmResponse(farm)).thenReturn(buildResponse());

            Page<FarmResponse> result = service.search(null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
