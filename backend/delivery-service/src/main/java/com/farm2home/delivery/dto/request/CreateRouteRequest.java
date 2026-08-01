package com.farm2home.delivery.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRouteRequest {

    @NotBlank(message = "Route name is required")
    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Route name must be at most 100 characters")
    @Schema(description = "Human-readable route name.", example = "North Zone Route 1",
            requiredMode = Schema.RequiredMode.REQUIRED, maxLength = ValidationConstants.NAME_MAX_LENGTH)
    private String routeName;

    @NotBlank(message = "Route code is required")
    @Size(max = 20, message = "Route code must be at most 20 characters")
    @Schema(description = "Short unique route code. Must be unique among non-deleted routes "
            + "(DeliveryRouteServiceImpl rejects duplicates with a 400). Stored upper-cased "
            + "regardless of the casing submitted (DeliveryMapper.toEntity uppercases it).",
            example = "NZ-01", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
    private String routeCode;

    @NotBlank(message = "Area is required")
    @Schema(description = "Locality/area served by this route.", example = "Whitefield",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String area;

    @NotBlank(message = "City is required")
    @Schema(description = "City served by this route.", example = "Bengaluru",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @NotBlank(message = "Pincode is required")
    @Size(min = ValidationConstants.PINCODE_MIN_LENGTH, max = ValidationConstants.PINCODE_MAX_LENGTH, message = "Pincode must be between 6 and 10 characters")
    @Schema(description = "Postal code, 6-10 characters.", example = "560066",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = ValidationConstants.PINCODE_MIN_LENGTH, maxLength = ValidationConstants.PINCODE_MAX_LENGTH)
    private String pincode;
}
