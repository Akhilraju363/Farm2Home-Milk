package com.farm2home.inventory.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl {

    private final ProductCategoryRepository repository;
    private final ProductRepository productRepository;
    private final ProductCategoryMapper mapper;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "ProductCategory")
    public ProductCategoryResponse create(CreateProductCategoryRequest request) {
        if (repository.existsByNameIgnoreCaseAndDeletedFalse(request.getName())) {
            throw new DuplicateResourceException("A category named '" + request.getName() + "' already exists");
        }
        ProductCategory category = mapper.toEntity(request);
        return mapper.toResponse(repository.save(category));
    }

    @Transactional(readOnly = true)
    public Page<ProductCategoryResponse> findAll(boolean activeOnly, Pageable pageable) {
        Page<ProductCategory> page = activeOnly
                ? repository.findAllByActiveTrueAndDeletedFalse(pageable)
                : repository.findAllByDeletedFalse(pageable);
        return page.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse findById(UUID id) {
        return mapper.toResponse(getCategory(id));
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "ProductCategory")
    public ProductCategoryResponse update(UUID id, UpdateProductCategoryRequest request) {
        ProductCategory category = getCategory(id);
        if (StringUtils.hasText(request.getName()) && !request.getName().equalsIgnoreCase(category.getName())
                && repository.existsByNameIgnoreCaseAndDeletedFalse(request.getName())) {
            throw new DuplicateResourceException("A category named '" + request.getName() + "' already exists");
        }
        mapper.updateEntityFromRequest(request, category);
        return mapper.toResponse(repository.save(category));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "ProductCategory")
    public void delete(UUID id) {
        ProductCategory category = getCategory(id);
        if (productRepository.existsByCategory_IdAndDeletedFalse(id)) {
            throw new CategoryInUseException(
                    "This category cannot be deleted because it is assigned to existing products");
        }
        category.setDeleted(true);
        repository.save(category);
    }

    private ProductCategory getCategory(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found: " + id));
    }
}
