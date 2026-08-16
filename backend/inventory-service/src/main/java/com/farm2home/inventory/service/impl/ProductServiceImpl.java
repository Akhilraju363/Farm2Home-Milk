package com.farm2home.inventory.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.common.core.constants.FileConstants;
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.export.TabularExporterFactory;
import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.inventory.domain.entity.Product;
import com.farm2home.inventory.domain.entity.ProductCategory;
import com.farm2home.inventory.domain.enums.ProductStockStatus;
import com.farm2home.inventory.domain.repository.ProductCategoryRepository;
import com.farm2home.inventory.domain.repository.ProductRepository;
import com.farm2home.inventory.domain.repository.ProductSpecifications;
import com.farm2home.inventory.dto.request.CreateProductRequest;
import com.farm2home.inventory.dto.request.UpdateProductRequest;
import com.farm2home.inventory.dto.response.ProductResponse;
import com.farm2home.inventory.exception.InventoryException;
import com.farm2home.inventory.exception.ResourceNotFoundException;
import com.farm2home.inventory.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl {

    private static final String UPLOAD_CATEGORY = FileConstants.CATEGORY_PRODUCTS;

    private final ProductRepository repository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductMapper mapper;
    private final FileStorageService fileStorageService;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "Product")
    public ProductResponse create(CreateProductRequest request) {
        Product product = mapper.toEntity(request);
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setStockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0);
        product.setMinimumStockQuantity(request.getMinimumStockQuantity() != null ? request.getMinimumStockQuantity() : 0);
        return mapper.toResponse(repository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(boolean activeOnly, Pageable pageable) {
        Page<Product> page = activeOnly
                ? repository.findAllByActiveTrueAndDeletedFalse(pageable)
                : repository.findAllByDeletedFalse(pageable);
        return page.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        return mapper.toResponse(getProduct(id));
    }

    /** Called by order-service at order-creation/checkout time to atomically reserve stock - see
     *  ProductRepository.decrementStock for why a single UPDATE...WHERE is what actually makes
     *  this race-safe under concurrent checkouts, not any locking done here. 404 if the product
     *  doesn't exist at all; 409 if it exists but doesn't currently have enough stock (a real
     *  conflict with concurrent state, not a malformed request - matches this codebase's
     *  ConflictException convention, e.g. delivery-service's route-deletion guard). */
    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Product")
    public ProductResponse decrementStock(UUID id, int quantity) {
        // Confirms existence up front (a clean 404 for an unknown/deleted id) before attempting
        // the atomic UPDATE below.
        getProduct(id);
        int updated = repository.decrementStock(id, quantity);
        Product current = getProduct(id);
        if (updated == 0) {
            throw new com.farm2home.common.web.exception.ConflictException(
                    "Only " + current.getStockQuantity() + " " + current.getName() + " available right now.");
        }
        return mapper.toResponse(current);
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Product")
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = getProduct(id);
        mapper.updateEntityFromRequest(request, product);
        if (request.getCategoryId() != null) {
            product.setCategory(resolveCategory(request.getCategoryId()));
        }
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getMinimumStockQuantity() != null) {
            product.setMinimumStockQuantity(request.getMinimumStockQuantity());
        }
        return mapper.toResponse(repository.save(product));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "Product")
    public void delete(UUID id) {
        Product product = getProduct(id);
        product.setDeleted(true);
        repository.save(product);
    }

    @Transactional
    @Audited(action = AuditAction.UPLOAD, entityType = "Product")
    public ProductResponse uploadImage(UUID id, MultipartFile file) {
        Product product = getProduct(id);
        String relativePath = fileStorageService.store(file, UPLOAD_CATEGORY);
        product.setImageUrl("/uploads/" + relativePath);
        return mapper.toResponse(repository.save(product));
    }

    private Product getProduct(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    /** Null categoryId means "uncategorized" and is left as null; a non-null categoryId must
     *  resolve to an existing, active category or the request is rejected as a 400 - it is never
     *  auto-created or silently dropped. */
    private ProductCategory resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndActiveTrueAndDeletedFalse(categoryId)
                .orElseThrow(() -> new InventoryException("Category not found or inactive: " + categoryId));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String keyword, LocalDate dateFrom, LocalDate dateTo, Boolean active,
            UUID categoryId, ProductStockStatus stockStatus, Boolean available, Pageable pageable) {
        Specification<Product> spec = buildSearchSpecification(
                keyword, dateFrom, dateTo, active, categoryId, stockStatus, available);
        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    /** Shared by both {@link #search} and {@link #export} so the two always see the exact same
     *  filtered result set. */
    private Specification<Product> buildSearchSpecification(String keyword, LocalDate dateFrom, LocalDate dateTo,
            Boolean active, UUID categoryId, ProductStockStatus stockStatus, Boolean available) {
        Specification<Product> spec = Specification.where(ProductSpecifications.notDeleted());
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(ProductSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(ProductSpecifications.createdBetween(dateFrom, dateTo));
        }
        if (active != null) {
            spec = spec.and(ProductSpecifications.isActive(active));
        }
        if (categoryId != null) {
            spec = spec.and(ProductSpecifications.hasCategory(categoryId));
        }
        if (stockStatus != null) {
            spec = spec.and(ProductSpecifications.hasStockStatus(stockStatus));
        }
        if (available != null) {
            spec = spec.and(ProductSpecifications.isAvailable(available));
        }
        return spec;
    }

    /** Streams matching products straight to {@code out} in the requested file format, one
     *  bounded page at a time, so exporting a very large product catalog never requires holding
     *  the full result set in memory. Each batch fetch runs in its own short-lived Spring Data
     *  transaction (this method is deliberately NOT wrapped in a single @Transactional so a
     *  slow export doesn't pin one DB connection for its entire duration). Runs on the async
     *  StreamingResponseBody dispatch thread, not the original request thread. */
    public void export(ExportFormat format, OutputStream out, String keyword, LocalDate dateFrom, LocalDate dateTo,
            Boolean active, UUID categoryId, ProductStockStatus stockStatus, Boolean available,
            String sortBy, boolean ascending) throws IOException {
        Specification<Product> spec = buildSearchSpecification(
                keyword, dateFrom, dateTo, active, categoryId, stockStatus, available);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        List<ExportColumn<Product>> columns = List.of(
                new ExportColumn<>("Name", Product::getName),
                new ExportColumn<>("Category", p -> p.getCategory() == null ? "" : p.getCategory().getName()),
                new ExportColumn<>("Price", p -> p.getPrice().toString()),
                new ExportColumn<>("Unit", p -> p.getUnit() == null ? "" : p.getUnit().name()),
                new ExportColumn<>("Stock Quantity", p -> String.valueOf(p.getStockQuantity())),
                new ExportColumn<>("Active", p -> String.valueOf(p.isActive())),
                new ExportColumn<>("Created At", p -> p.getCreatedAt() == null ? "" : p.getCreatedAt().toString()));

        BatchSupplier<Product> supplier = (page, size) ->
                repository.findAll(spec, PageRequest.of(page, size, sort)).getContent();

        TabularExporterFactory.<Product>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }
}
