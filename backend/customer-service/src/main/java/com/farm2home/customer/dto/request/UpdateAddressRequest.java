package com.farm2home.customer.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
}
