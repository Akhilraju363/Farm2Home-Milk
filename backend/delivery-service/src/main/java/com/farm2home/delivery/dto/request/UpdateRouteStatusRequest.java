package com.farm2home.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRouteStatusRequest {

    @NotNull(message = "Active is required")
    @Schema(description = "true to activate, false to deactivate. Independent of soft-delete - "
            + "an inactive route still appears in GET / and GET /{id}, it just cannot be selected "
            + "for a new delivery assignment.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean active;
}
