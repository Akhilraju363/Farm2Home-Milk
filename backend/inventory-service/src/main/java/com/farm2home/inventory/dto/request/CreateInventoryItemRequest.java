package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.enums.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Item name must be at most 100 characters")
    @Schema(description = "Item name. Required, max 100 characters.", example = "Cattle Feed - Premium Mix")
    private String itemName;

    @NotNull(message = "Item type is required")
    @Schema(description = "Category of inventory item. Required.", example = "FEED")
    private ItemType itemType;

    @NotNull(message = "Unit is required")
    @Schema(description = "Unit of measure the quantity/reorder level are expressed in. Required.", example = "KG")
    private UnitType unit;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "Reorder level cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Reorder level must have up to 8 digits before and 2 digits after the decimal point")
    @Schema(description = "Quantity threshold at/below which the item is flagged as low-stock. Optional; "
            + "cannot be negative.", example = "50.00")
    private BigDecimal reorderLevel;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "Unit price cannot be negative")
    @Digits(integer = 6, fraction = 2, message = "Unit price must have up to 6 digits before and 2 digits after the decimal point")
    @Schema(description = "Price per unit, in rupees. Optional; cannot be negative.", example = "32.50")
    private BigDecimal unitPrice;

    @Size(max = 100, message = "Supplier must be at most 100 characters")
    @Schema(description = "Supplier name. Optional, max 100 characters.", example = "Green Valley Suppliers")
    private String supplier;
}
