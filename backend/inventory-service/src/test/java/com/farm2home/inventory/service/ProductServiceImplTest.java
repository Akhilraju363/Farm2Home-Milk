package com.farm2home.inventory.service;

import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.inventory.domain.entity.Product;
import com.farm2home.inventory.domain.repository.ProductRepository;
import com.farm2home.inventory.dto.request.CreateProductRequest;
import com.farm2home.inventory.dto.request.UpdateProductRequest;
import com.farm2home.inventory.dto.response.ProductResponse;
import com.farm2home.inventory.exception.ResourceNotFoundException;
import com.farm2home.inventory.mapper.ProductMapper;
import com.farm2home.inventory.service.impl.ProductServiceImpl;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
class ProductServiceImplTest {

    @Mock private ProductRepository repository;
    @Mock private ProductMapper mapper;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private ProductServiceImpl service;

    private final UUID productId = UUID.randomUUID();

    private Product buildProduct() {
        return Product.builder()
                .id(productId).name("Full Cream Milk").price(new BigDecimal("80.00"))
                .active(true).deleted(false).build();
    }

    private ProductResponse buildResponse() {
        return ProductResponse.builder().id(productId).name("Full Cream Milk")
                .price(new BigDecimal("80.00")).active(true).build();
    }

    @Nested @DisplayName("create()")
    class Create {
        @Test
        @DisplayName("valid request → saves product")
        void happyPath() {
            CreateProductRequest req = new CreateProductRequest();
            req.setName("Full Cream Milk");
            req.setPrice(new BigDecimal("80.00"));
            Product entity = buildProduct();
            when(mapper.toEntity(req)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            ProductResponse result = service.create(req);

            assertThat(result.getName()).isEqualTo("Full Cream Milk");
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("findAll()")
    class FindAll {
        @Test
        @DisplayName("activeOnly=true → queries active products")
        void activeOnly_queriesActive() {
            when(repository.findAllByActiveTrueAndDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(buildProduct())));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findAll(true, org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
            verify(repository, never()).findAllByDeletedFalse(any());
        }

        @Test
        @DisplayName("activeOnly=false → queries all")
        void allProducts_queriesAll() {
            when(repository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(buildProduct())));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findAll(false, org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested @DisplayName("findById()")
    class FindById {
        @Test
        @DisplayName("existing product → returns response")
        void found() {
            when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.of(buildProduct()));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(productId).getId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("missing product → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(productId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("update()")
    class Update {
        @Test
        @DisplayName("existing product → applies changes and saves")
        void happyPath() {
            Product entity = buildProduct();
            UpdateProductRequest req = new UpdateProductRequest();
            req.setPrice(new BigDecimal("85.00"));
            when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.update(productId, req);

            verify(mapper).updateEntityFromRequest(req, entity);
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {
        @Test
        @DisplayName("existing product → sets deleted=true")
        void softDeletes() {
            Product entity = buildProduct();
            when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.of(entity));

            service.delete(productId);

            assertThat(entity.isDeleted()).isTrue();
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("uploadImage()")
    class UploadImage {
        @Test
        @DisplayName("valid file → stores and sets imageUrl")
        void happyPath() {
            Product entity = buildProduct();
            MockMultipartFile file = new MockMultipartFile("file", "milk.jpg", "image/jpeg", new byte[]{1, 2, 3});
            when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.of(entity));
            when(fileStorageService.store(file, "products")).thenReturn("products/uuid.jpg");
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.uploadImage(productId, file);

            assertThat(entity.getImageUrl()).isEqualTo("/uploads/products/uuid.jpg");
            verify(repository).save(entity);
        }
    }

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("keyword + active + date range all combine into one query")
        void allFiltersCombine() {
            Product product = buildProduct();
            var page = new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toResponse(product)).thenReturn(buildResponse());

            Page<ProductResponse> result = service.search(
                    "milk", LocalDate.now().minusDays(7), LocalDate.now(), true, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("blank keyword → keyword predicate is not applied")
        void blankKeyword_notApplied() {
            var page = new PageImpl<Product>(List.of(), PageRequest.of(0, 20), 0);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            Page<ProductResponse> result = service.search("   ", null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("no filters → returns all non-deleted products")
        void noFilters() {
            Product product = buildProduct();
            var page = new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toResponse(product)).thenReturn(buildResponse());

            Page<ProductResponse> result = service.search(null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("export()")
    class Export {

        @Test
        @DisplayName("CSV format → streams matching products as CSV rows")
        void csv_streamsMatchingRows() throws Exception {
            Product product = buildProduct();
            var firstPage = new PageImpl<>(List.of(product),
                    PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("name").ascending()), 1);
            var emptyPage = new PageImpl<Product>(List.of());
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(firstPage, emptyPage);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, "milk", null, null, true, "name", true);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).contains("Name").contains("Full Cream Milk");
        }

        @Test
        @DisplayName("no matching products → writes header only, no rows")
        void noMatches_writesHeaderOnly() throws Exception {
            when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<Product>(List.of()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, null, null, null, null, "name", false);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content.trim()).isEqualTo("Name,Category,Price,Active,Created At");
        }
    }
}
