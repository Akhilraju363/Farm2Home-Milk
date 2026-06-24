package com.farm2home.inventory.dto.request;

import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.enums.UnitType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryItemRequest {

    @NotBlank @Size(max = 100)
    private String itemName;

    @NotNull
    private ItemType itemType;

    @NotNull
    private UnitType unit;

    @DecimalMin(value = "0.0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal reorderLevel;

    @DecimalMin(value = "0.0")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal unitPrice;

    @Size(max = 100)
    private String supplier;
}
