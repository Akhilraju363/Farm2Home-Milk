package com.farm2home.farm.dto.request;

import com.farm2home.farm.domain.enums.CowStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCowStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "New status for the cow. From ACTIVE or SICK, any status is reachable; "
            + "from SOLD or DECEASED (terminal), no transition is allowed.",
            example = "SICK")
    private CowStatus status;
}
