package com.farm2home.customer.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

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

    // Optional: captured client-side via browser geolocation or a map picker (see
    // AddressFormDialog.tsx/RegisterPage.tsx) - never derived from city/pincode server-side, so a
    // caller that doesn't collect a real device/map position simply omits these and the address
    // stays coordinate-less (delivery-availability then reports "cannot be determined").
    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    @Schema(description = "Optional - real device/map-captured latitude.", example = "13.6275")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    @Schema(description = "Optional - real device/map-captured longitude.", example = "78.9691")
    private BigDecimal longitude;
}
