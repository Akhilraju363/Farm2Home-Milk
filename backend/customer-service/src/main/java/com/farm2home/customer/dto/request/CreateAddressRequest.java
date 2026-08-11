package com.farm2home.customer.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAddressRequest {

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must be at most 255 characters")
    @Schema(description = "House/flat number and street.", example = "402, Block A, Maple Avenue")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must be at most 255 characters")
    @Schema(description = "Optional second line (area/locality/landmark).", example = "Green Hills Colony")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be at most 100 characters")
    @Schema(example = "Mumbai")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must be at most 100 characters")
    @Schema(example = "Maharashtra")
    private String state;

    // Optional: not every caller of this endpoint (e.g. the admin Customer Management address
    // dialog) collects a district - only the location-dropdown-driven registration Address step does.
    @Size(max = 100, message = "District must be at most 100 characters")
    @Schema(example = "Mumbai Suburban")
    private String district;

    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = RegexConstants.PINCODE_PATTERN, message = "Enter a valid 6-digit pincode")
    @Schema(example = "400001")
    private String pincode;
}
