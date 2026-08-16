package com.farm2home.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Records every purpose's consent choice shown on a single screen at once "
        + "(e.g. all four registration checkboxes) in one call.")
public class ConsentBulkUpdateRequest {

    @NotEmpty(message = "At least one consent choice is required")
    @Valid
    private List<ConsentUpdateRequest> consents;
}
