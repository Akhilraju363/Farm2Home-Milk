package com.farm2home.production.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.production.domain.enums.QualityGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Partial update - any field left null is left unchanged on the existing record. "
        + "cowId, collectionDate, and session cannot be changed here.")
public class UpdateMilkProductionRequest {

    @DecimalMin(value = ValidationConstants.MIN_POSITIVE_AMOUNT, message = "Quantity must be greater than zero")
    @Digits(integer = 4, fraction = 2, message = "Quantity must have up to 4 digits before and 2 digits after the decimal point")
    @Schema(description = "New quantity, in liters. Omit/null to leave unchanged. Must be greater than zero.",
            example = "13.00")
    private BigDecimal quantityLiters;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "Fat percentage cannot be negative")
    @DecimalMax(value = "99.99", message = "Fat percentage cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "Fat percentage must have up to 2 digits before and 2 digits after the decimal point")
    @Schema(description = "New fat content percentage. Omit/null to leave unchanged. 0-99.99.", example = "4.30")
    private BigDecimal fatPercentage;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "SNF percentage cannot be negative")
    @DecimalMax(value = "99.99", message = "SNF percentage cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "SNF percentage must have up to 2 digits before and 2 digits after the decimal point")
    @Schema(description = "New SNF content percentage. Omit/null to leave unchanged. 0-99.99.", example = "8.70")
    private BigDecimal snfPercentage;

    @Schema(description = "New quality grade. Omit/null to leave unchanged.", example = "A")
    private QualityGrade qualityGrade;

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Collected by must be at most 100 characters")
    @Schema(description = "New collector name. Omit/null to leave unchanged. Max 100 characters.",
            example = "Ramesh Kumar")
    private String collectedBy;

    @Size(max = ValidationConstants.NOTES_MAX_LENGTH, message = "Notes must be at most 255 characters")
    @Schema(description = "New notes. Omit/null to leave unchanged. Max 255 characters.", example = "Corrected quantity")
    private String notes;
}
