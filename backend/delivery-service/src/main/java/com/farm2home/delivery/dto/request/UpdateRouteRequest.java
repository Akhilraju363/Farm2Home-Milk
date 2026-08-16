package com.farm2home.delivery.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Partial update: every field is optional, and only non-null (DeliveryMapper additionally treats
 * blank strings as "unchanged", not just null - see {@code @Condition hasText} in DeliveryMapper)
 * values overwrite the existing route. routeCode cannot be changed via this endpoint. These
 * bean-validation constraints are enforced - DeliveryRouteController.update() now carries
 * {@code @Valid}.
 */
@Data
public class UpdateRouteRequest {

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Route name must be at most 100 characters")
    @Schema(description = "New route name, up to 100 characters. Omit/null/blank to leave "
            + "unchanged.", example = "North Zone Route 1", maxLength = ValidationConstants.NAME_MAX_LENGTH)
    private String routeName;

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Area must be at most 100 characters")
    @Schema(description = "New area, up to 100 characters. Omit/null/blank to leave unchanged.",
            example = "Whitefield", maxLength = ValidationConstants.NAME_MAX_LENGTH)
    private String area;

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "City must be at most 100 characters")
    @Schema(description = "New city, up to 100 characters. Omit/null/blank to leave unchanged.",
            example = "Bengaluru", maxLength = ValidationConstants.NAME_MAX_LENGTH)
    private String city;

    @Size(min = ValidationConstants.PINCODE_MIN_LENGTH, max = ValidationConstants.PINCODE_MAX_LENGTH, message = "Pincode must be between 6 and 10 characters")
    @Schema(description = "New pincode, 6-10 characters. Omit/null/blank to leave unchanged.",
            example = "560066", minLength = ValidationConstants.PINCODE_MIN_LENGTH, maxLength = ValidationConstants.PINCODE_MAX_LENGTH)
    private String pincode;

    @Schema(description = "Activate/deactivate the route. Omit/null to leave unchanged. Note: "
            + "this is independent of soft-delete (DELETE /{id}) - GET / and GET /{id} do not "
            + "filter by this flag, only by soft-delete status.", example = "true")
    private Boolean active;

    @DecimalMin(value = "-90", message = "Center latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Center latitude must be between -90 and 90")
    @Schema(description = "New center latitude for automatic route selection. Omit/null to leave unchanged.", example = "13.67199825")
    private BigDecimal centerLatitude;

    @DecimalMin(value = "-180", message = "Center longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Center longitude must be between -180 and 180")
    @Schema(description = "New center longitude. Omit/null to leave unchanged.", example = "78.96885066")
    private BigDecimal centerLongitude;

    @Positive(message = "Coverage radius must be greater than 0")
    @Schema(description = "New coverage radius in kilometers. Omit/null to leave unchanged.", example = "7.0")
    private BigDecimal radiusKm;
}
