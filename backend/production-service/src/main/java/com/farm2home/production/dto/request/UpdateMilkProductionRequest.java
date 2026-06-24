package com.farm2home.production.dto.request;

import com.farm2home.production.domain.enums.QualityGrade;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateMilkProductionRequest {

    @DecimalMin(value = "0.01")
    @Digits(integer = 4, fraction = 2)
    private BigDecimal quantityLiters;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "99.99")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal fatPercentage;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "99.99")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal snfPercentage;

    private QualityGrade qualityGrade;

    @Size(max = 100)
    private String collectedBy;

    @Size(max = 255)
    private String notes;
}
