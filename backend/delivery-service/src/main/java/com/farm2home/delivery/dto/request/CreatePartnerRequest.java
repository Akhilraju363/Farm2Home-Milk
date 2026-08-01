package com.farm2home.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePartnerRequest {

    @NotNull(message = "User ID is required")
    @Schema(description = "auth-service user id of the delivery partner's account. Not validated "
            + "against auth-service here - it is trusted as-is and simply stored on the partner "
            + "record (see DeliveryPartner.userId, used later by UserPrincipal-based ownership "
            + "checks to resolve \"my assignments\").",
            example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "Optional delivery route to pre-assign this partner to. Must reference "
            + "an existing, non-deleted route if provided.",
            example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID routeId;

    @NotBlank(message = "Name is required")
    @Schema(description = "Delivery partner's display name.", example = "Ramesh Kumar",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Mobile is required")
    @Schema(description = "Contact mobile number. Unlike UpdatePartnerRequest.mobile, this field "
            + "is not pattern-validated at creation time - only presence is enforced.",
            example = "9876543210", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mobile;

    @Schema(description = "Free-text vehicle type/description.", example = "Two-wheeler")
    private String vehicleType;
}
