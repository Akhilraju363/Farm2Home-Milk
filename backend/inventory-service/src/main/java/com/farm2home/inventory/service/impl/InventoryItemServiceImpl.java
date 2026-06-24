package com.farm2home.inventory.service.impl;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.repository.InventoryItemRepository;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.request.UpdateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.exception.ResourceNotFoundException;
import com.farm2home.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryItemServiceImpl {

    private final InventoryItemRepository repository;
    private final InventoryMapper mapper;

    @Transactional
    public InventoryItemResponse create(CreateInventoryItemRequest request) {
        InventoryItem item = InventoryItem.builder()
                .itemName(request.getItemName())
                .itemType(request.getItemType())
                .unit(request.getUnit())
                .reorderLevel(request.getReorderLevel() != null ? request.getReorderLevel() : java.math.BigDecimal.ZERO)
                .unitPrice(request.getUnitPrice())
                .supplier(request.getSupplier())
                .build();
        return mapper.toItemResponse(repository.save(item));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> findAll(com.farm2home.inventory.domain.enums.ItemType typeFilter, Pageable pageable) {
        if (typeFilter != null) {
            return repository.findAllByItemTypeAndDeletedFalse(typeFilter, pageable).map(mapper::toItemResponse);
        }
        return repository.findAllByDeletedFalse(pageable).map(mapper::toItemResponse);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse findById(UUID id) {
        return mapper.toItemResponse(getItem(id));
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> findLowStock() {
        return repository.findItemsBelowReorderLevel().stream().map(mapper::toItemResponse).toList();
    }

    @Transactional
    public InventoryItemResponse update(UUID id, UpdateInventoryItemRequest request) {
        InventoryItem item = getItem(id);
        if (StringUtils.hasText(request.getItemName()))  item.setItemName(request.getItemName());
        if (request.getReorderLevel() != null)            item.setReorderLevel(request.getReorderLevel());
        if (request.getUnitPrice()    != null)            item.setUnitPrice(request.getUnitPrice());
        if (StringUtils.hasText(request.getSupplier()))  item.setSupplier(request.getSupplier());
        return mapper.toItemResponse(repository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        InventoryItem item = getItem(id);
        item.setDeleted(true);
        repository.save(item);
    }

    public InventoryItem getItem(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));
    }
}
