package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.enums.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>,
        JpaSpecificationExecutor<InventoryItem> {

    Page<InventoryItem> findAllByDeletedFalse(Pageable pageable);

    Page<InventoryItem> findAllByItemTypeAndDeletedFalse(ItemType itemType, Pageable pageable);

    Optional<InventoryItem> findByIdAndDeletedFalse(UUID id);

    @Query("SELECT i FROM InventoryItem i WHERE i.quantity <= i.reorderLevel AND i.deleted = false")
    List<InventoryItem> findItemsBelowReorderLevel();

    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.quantity <= i.reorderLevel AND i.deleted = false")
    long countItemsBelowReorderLevel();
}
