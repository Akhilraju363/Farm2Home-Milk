package com.farm2home.farm.dto.request;

import com.farm2home.farm.domain.enums.CowStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCowStatusRequest {

    @NotNull(message = "Status is required")
    private CowStatus status;
}
