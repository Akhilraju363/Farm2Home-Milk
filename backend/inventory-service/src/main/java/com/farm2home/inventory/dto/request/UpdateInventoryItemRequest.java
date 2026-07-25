package com.farm2home.inventory.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateInventoryItemRequest {

    @Size(max = 100, message = "Item name must be at most 100 characters")
    private String itemName;

    @DecimalMin(value = "0.0", message = "Reorder level cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Reorder level must have up to 8 digits before and 2 digits after the decimal point")
    private BigDecimal reorderLevel;

    @DecimalMin(value = "0.0", message = "Unit price cannot be negative")
    @Digits(integer = 6, fraction = 2, message = "Unit price must have up to 6 digits before and 2 digits after the decimal point")
    private BigDecimal unitPrice;

    @Size(max = 100, message = "Supplier must be at most 100 characters")
    private String supplier;
}
