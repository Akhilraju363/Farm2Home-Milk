package com.farm2home.inventory.mapper;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.entity.StockTransaction;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
