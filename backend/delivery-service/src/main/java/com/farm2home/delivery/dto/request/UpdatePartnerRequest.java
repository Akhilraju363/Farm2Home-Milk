package com.farm2home.delivery.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Partial update: every field is optional, and only non-null (DeliveryMapper additionally treats
 * blank strings as "unchanged", not just null - see {@code @Condition hasText} in DeliveryMapper)
 * values overwrite the existing partner. These bean-validation constraints are enforced -
 * DeliveryPartnerController.update() now carries {@code @Valid}.
 */
@Data
public class UpdatePartnerRequest {

    @Schema(description = "Reassign the partner to a different route. Must reference an existing, "
            + "non-deleted route if provided. Omit/null to leave the current route unchanged.",
            example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID routeId;

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Name must be at most 100 characters")
    @Schema(description = "New display name, up to 100 characters. Omit/null/blank to leave "
            + "unchanged.", example = "Ramesh Kumar", maxLength = ValidationConstants.NAME_MAX_LENGTH)
    private String name;

    @Pattern(regexp = RegexConstants.MOBILE_PATTERN, message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "New contact mobile number - unlike CreatePartnerRequest.mobile, this "
            + "is pattern-validated: a 10-digit Indian mobile number starting with 6-9. "
            + "Omit/null/blank to leave unchanged.", example = "9876543210", pattern = RegexConstants.MOBILE_PATTERN)
    private String mobile;

    @Size(max = 50, message = "Vehicle type must be at most 50 characters")
    @Schema(description = "New vehicle type, up to 50 characters. Omit/null/blank to leave "
            + "unchanged.", example = "Four-wheeler", maxLength = 50)
    private String vehicleType;

    @Schema(description = "Activate/deactivate the partner. Omit/null to leave unchanged.", example = "true")
    private Boolean active;
}
