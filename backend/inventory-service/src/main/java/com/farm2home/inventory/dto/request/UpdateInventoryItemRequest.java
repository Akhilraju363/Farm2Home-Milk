package com.farm2home.inventory.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateInventoryItemRequest {

    @Size(max = 100)
    private String itemName;

    @DecimalMin(value = "0.0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal reorderLevel;

    @DecimalMin(value = "0.0")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal unitPrice;

    @Size(max = 100)
    private String supplier;
}
