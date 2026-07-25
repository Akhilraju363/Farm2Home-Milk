package com.farm2home.inventory.mapper;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.entity.StockTransaction;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.request.UpdateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "itemType",          expression = "java(item.getItemType().name())")
    @Mapping(target = "unit",              expression = "java(item.getUnit().name())")
    @Mapping(target = "belowReorderLevel", expression = "java(item.getQuantity().compareTo(item.getReorderLevel()) <= 0)")
    InventoryItemResponse toItemResponse(InventoryItem item);

    @Mapping(target = "itemId",   expression = "java(txn.getItem().getId())")
    @Mapping(target = "itemName", expression = "java(txn.getItem().getItemName())")
    @Mapping(target = "txnType",  expression = "java(txn.getTxnType().name())")
    StockTransactionResponse toTxnResponse(StockTransaction txn);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "reorderLevel", expression = "java(request.getReorderLevel() != null ? request.getReorderLevel() : java.math.BigDecimal.ZERO)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    InventoryItem toEntity(CreateInventoryItemRequest request);

    // hasText() below skips blank strings the same way the StringUtils.hasText guards it
    // replaces did; a plain null-check alone isn't equivalent for the String fields.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "itemType", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "unit", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateItemFromRequest(UpdateInventoryItemRequest request, @MappingTarget InventoryItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "item", ignore = true)
    @Mapping(target = "transactedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    StockTransaction toEntity(StockTransactionRequest request);

    @Condition
    default boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
