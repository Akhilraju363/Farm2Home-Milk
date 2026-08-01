package com.farm2home.production.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.production.domain.enums.MilkSession;
import com.farm2home.production.domain.enums.QualityGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "At most one record may exist per (cowId, collectionDate, session) - creating a duplicate "
        + "is rejected. This service does not validate the cow's status (e.g. active/sold) before accepting a record.")
public class CreateMilkProductionRequest {

    @NotNull(message = "Cow ID is required")
    @Schema(description = "ID of the cow this collection is for. Required.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID cowId;

    @NotNull(message = "Collection date is required")
    @PastOrPresent(message = "Collection date cannot be in the future")
    @Schema(description = "Date the milk was collected. Required, cannot be in the future.", example = "2026-07-31")
    private LocalDate collectionDate;

    @NotNull(message = "Session is required")
    @Schema(description = "Collection session. Required. Combined with cowId + collectionDate, uniquely "
            + "identifies a record.", example = "MORNING")
    private MilkSession session;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = ValidationConstants.MIN_POSITIVE_AMOUNT, message = "Quantity must be greater than zero")
    @Digits(integer = 4, fraction = 2, message = "Quantity must have up to 4 digits before and 2 digits after the decimal point")
    @Schema(description = "Milk collected, in liters. Required, must be greater than zero.", example = "12.50")
    private BigDecimal quantityLiters;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "Fat percentage cannot be negative")
    @DecimalMax(value = "99.99", message = "Fat percentage cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "Fat percentage must have up to 2 digits before and 2 digits after the decimal point")
    @Schema(description = "Fat content percentage. Optional, 0-99.99.", example = "4.20")
    private BigDecimal fatPercentage;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, message = "SNF percentage cannot be negative")
    @DecimalMax(value = "99.99", message = "SNF percentage cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "SNF percentage must have up to 2 digits before and 2 digits after the decimal point")
    @Schema(description = "Solids-not-fat content percentage. Optional, 0-99.99.", example = "8.60")
    private BigDecimal snfPercentage;

    @Schema(description = "Overall quality grade for this collection. Optional.", example = "A")
    private QualityGrade qualityGrade;

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Collected by must be at most 100 characters")
    @Schema(description = "Name of the person who collected the milk. Optional, max 100 characters.",
            example = "Ramesh Kumar")
    private String collectedBy;

    @Size(max = ValidationConstants.NOTES_MAX_LENGTH, message = "Notes must be at most 255 characters")
    @Schema(description = "Free-text notes. Optional, max 255 characters.", example = "Normal collection")
    private String notes;
}
