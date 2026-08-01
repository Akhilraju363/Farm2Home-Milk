package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Partial update - any field left null is left unchanged on the existing item. "
        + "There is no field to change quantity/itemType/unit here; quantity only moves via stock "
        + "transactions, and itemType/unit are immutable after creation.")
public class UpdateInventoryItemRequest {

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Item name must be at most 100 characters")
    @Schema(description = "New item name. Omit/null to leave unchanged. Max 100 characters.",
            example = "Cattle Feed - Premium Mix")
    private String itemName;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "Reorder level cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Reorder level must have up to 8 digits before and 2 digits after the decimal point")
    @Schema(description = "New reorder level. Omit/null to leave unchanged. Cannot be negative.", example = "50.00")
    private BigDecimal reorderLevel;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "Unit price cannot be negative")
    @Digits(integer = 6, fraction = 2, message = "Unit price must have up to 6 digits before and 2 digits after the decimal point")
    @Schema(description = "New unit price, in rupees. Omit/null to leave unchanged. Cannot be negative.", example = "32.50")
    private BigDecimal unitPrice;

    @Size(max = 100, message = "Supplier must be at most 100 characters")
    @Schema(description = "New supplier name. Omit/null to leave unchanged. Max 100 characters.",
            example = "Green Valley Suppliers")
    private String supplier;
}
