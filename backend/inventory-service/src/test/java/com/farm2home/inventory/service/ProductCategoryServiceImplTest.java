package com.farm2home.inventory.service;

import com.farm2home.inventory.domain.entity.ProductCategory;
import com.farm2home.inventory.domain.repository.ProductCategoryRepository;
import com.farm2home.inventory.domain.repository.ProductRepository;
import com.farm2home.inventory.dto.request.CreateProductCategoryRequest;
import com.farm2home.inventory.dto.request.UpdateProductCategoryRequest;
import com.farm2home.inventory.dto.response.ProductCategoryResponse;
import com.farm2home.inventory.exception.CategoryInUseException;
import com.farm2home.inventory.exception.DuplicateResourceException;
import com.farm2home.inventory.exception.ResourceNotFoundException;
import com.farm2home.inventory.mapper.ProductCategoryMapper;
import com.farm2home.inventory.service.impl.ProductCategoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @Mock private ProductCategoryRepository repository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryMapper mapper;

    @InjectMocks private ProductCategoryServiceImpl service;

    private final UUID categoryId = UUID.randomUUID();

    private ProductCategory buildCategory() {
        return ProductCategory.builder().id(categoryId).name("Milk").active(true).deleted(false).build();
    }

    private ProductCategoryResponse buildResponse() {
        return ProductCategoryResponse.builder().id(categoryId).name("Milk").active(true).build();
    }

    @Nested @DisplayName("create()")
    class Create {
        @Test
        @DisplayName("unique name → saves category")
        void happyPath() {
            CreateProductCategoryRequest req = new CreateProductCategoryRequest();
            req.setName("Milk");
            ProductCategory entity = buildCategory();
            when(repository.existsByNameIgnoreCaseAndDeletedFalse("Milk")).thenReturn(false);
            when(mapper.toEntity(req)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            ProductCategoryResponse result = service.create(req);

            assertThat(result.getName()).isEqualTo("Milk");
            verify(repository).save(entity);
        }

        @Test
        @DisplayName("duplicate name (case-insensitive) → throws DuplicateResourceException, never saves")
        void duplicateName_throws() {
            CreateProductCategoryRequest req = new CreateProductCategoryRequest();
            req.setName("milk");
            when(repository.existsByNameIgnoreCaseAndDeletedFalse("milk")).thenReturn(true);

            assertThatThrownBy(() -> service.create(req)).isInstanceOf(DuplicateResourceException.class);
            verify(repository, never()).save(any());
        }
    }

    @Nested @DisplayName("findAll()")
    class FindAll {
        @Test
        @DisplayName("activeOnly=true → queries active categories")
        void activeOnly_queriesActive() {
            when(repository.findAllByActiveTrueAndDeletedFalse(any())).thenReturn(new PageImpl<>(java.util.List.of(buildCategory())));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findAll(true, Pageable.unpaged()).getTotalElements()).isEqualTo(1);
            verify(repository, never()).findAllByDeletedFalse(any());
        }

        @Test
        @DisplayName("activeOnly=false → queries all")
        void allCategories_queriesAll() {
            when(repository.findAllByDeletedFalse(any())).thenReturn(new PageImpl<>(java.util.List.of(buildCategory())));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findAll(false, Pageable.unpaged()).getTotalElements()).isEqualTo(1);
        }
    }

    @Nested @DisplayName("findById()")
    class FindById {
        @Test
        @DisplayName("existing category → returns response")
        void found() {
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.of(buildCategory()));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(categoryId).getId()).isEqualTo(categoryId);
        }

        @Test
        @DisplayName("missing category → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(categoryId)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("update()")
    class Update {
        @Test
        @DisplayName("existing category, unchanged name → applies changes and saves")
        void happyPath() {
            ProductCategory entity = buildCategory();
            UpdateProductCategoryRequest req = new UpdateProductCategoryRequest();
            req.setDescription("Fresh dairy milk");
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.update(categoryId, req);

            verify(mapper).updateEntityFromRequest(req, entity);
            verify(repository).save(entity);
            verify(repository, never()).existsByNameIgnoreCaseAndDeletedFalse(any());
        }

        @Test
        @DisplayName("new name matching an existing category → throws DuplicateResourceException")
        void duplicateName_throws() {
            ProductCategory entity = buildCategory();
            UpdateProductCategoryRequest req = new UpdateProductCategoryRequest();
            req.setName("Cheese");
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.of(entity));
            when(repository.existsByNameIgnoreCaseAndDeletedFalse("Cheese")).thenReturn(true);

            assertThatThrownBy(() -> service.update(categoryId, req)).isInstanceOf(DuplicateResourceException.class);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("new name identical to current (case-insensitive) → not treated as a duplicate")
        void sameNameDifferentCase_notDuplicate() {
            ProductCategory entity = buildCategory();
            UpdateProductCategoryRequest req = new UpdateProductCategoryRequest();
            req.setName("MILK");
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.update(categoryId, req);

            verify(repository, never()).existsByNameIgnoreCaseAndDeletedFalse(any());
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {
        @Test
        @DisplayName("existing category, not in use → sets deleted=true")
        void softDeletes() {
            ProductCategory entity = buildCategory();
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.of(entity));
            when(productRepository.existsByCategory_IdAndDeletedFalse(categoryId)).thenReturn(false);

            service.delete(categoryId);

            assertThat(entity.isDeleted()).isTrue();
            verify(repository).save(entity);
        }

        @Test
        @DisplayName("category still assigned to a live product → throws CategoryInUseException, never saves")
        void inUse_throws() {
            ProductCategory entity = buildCategory();
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.of(entity));
            when(productRepository.existsByCategory_IdAndDeletedFalse(categoryId)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(categoryId)).isInstanceOf(CategoryInUseException.class);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("missing category → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(categoryId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.delete(categoryId)).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
