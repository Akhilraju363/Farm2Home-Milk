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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryItemServiceImpl {

    private final InventoryItemRepository repository;
    private final InventoryMapper mapper;

    @Transactional
    public InventoryItemResponse create(CreateInventoryItemRequest request) {
        InventoryItem item = mapper.toEntity(request);
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
        mapper.updateItemFromRequest(request, item);
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
