package com.farm2home.customer.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Partial update - every field is optional and left null (unchanged) on the entity if omitted,
 *  matching UpdateCustomerRequest's convention. */
@Data
public class UpdateAddressRequest {

    @Size(max = 255, message = "Address line 1 must be at most 255 characters")
    @Schema(description = "Leave null to keep the existing value.", example = "402, Block A, Maple Avenue")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must be at most 255 characters")
    @Schema(description = "Leave null to keep the existing value.", example = "Green Hills Colony")
    private String addressLine2;

    @Size(max = 100, message = "City must be at most 100 characters")
    @Schema(description = "Leave null to keep the existing value.", example = "Mumbai")
    private String city;

    @Size(max = 100, message = "State must be at most 100 characters")
    @Schema(description = "Leave null to keep the existing value.", example = "Maharashtra")
    private String state;

    @Size(max = 100, message = "District must be at most 100 characters")
    @Schema(description = "Leave null to keep the existing value.", example = "Mumbai Suburban")
    private String district;

    @Pattern(regexp = RegexConstants.PINCODE_PATTERN, message = "Enter a valid 6-digit pincode")
    @Schema(description = "Leave null to keep the existing value.", example = "400001")
    private String pincode;

    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    @Schema(description = "Leave null to keep the existing value.", example = "13.6275")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    @Schema(description = "Leave null to keep the existing value.", example = "78.9691")
    private BigDecimal longitude;
}
