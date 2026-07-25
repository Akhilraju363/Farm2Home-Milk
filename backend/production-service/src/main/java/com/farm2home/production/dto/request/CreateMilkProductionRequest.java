package com.farm2home.production.dto.request;

import com.farm2home.production.domain.enums.MilkSession;
import com.farm2home.production.domain.enums.QualityGrade;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateMilkProductionRequest {

    @NotNull(message = "Cow ID is required")
    private UUID cowId;

    @NotNull(message = "Collection date is required")
    @PastOrPresent(message = "Collection date cannot be in the future")
    private LocalDate collectionDate;

    @NotNull(message = "Session is required")
    private MilkSession session;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    @Digits(integer = 4, fraction = 2, message = "Quantity must have up to 4 digits before and 2 digits after the decimal point")
    private BigDecimal quantityLiters;

    @DecimalMin(value = "0.0", message = "Fat percentage cannot be negative")
    @DecimalMax(value = "99.99", message = "Fat percentage cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "Fat percentage must have up to 2 digits before and 2 digits after the decimal point")
    private BigDecimal fatPercentage;

    @DecimalMin(value = "0.0", message = "SNF percentage cannot be negative")
    @DecimalMax(value = "99.99", message = "SNF percentage cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "SNF percentage must have up to 2 digits before and 2 digits after the decimal point")
    private BigDecimal snfPercentage;

    private QualityGrade qualityGrade;

    @Size(max = 100, message = "Collected by must be at most 100 characters")
    private String collectedBy;

    @Size(max = 255, message = "Notes must be at most 255 characters")
    private String notes;
}
