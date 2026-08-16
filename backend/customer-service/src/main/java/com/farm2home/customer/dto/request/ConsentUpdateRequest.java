package com.farm2home.customer.dto.request;

import com.farm2home.customer.domain.enums.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "One purpose's consent state. granted=false records an explicit opt-out/withdrawal, "
        + "not an absence of a record - see ConsentRecord.")
public class ConsentUpdateRequest {

    @NotNull(message = "Purpose is required")
    private ConsentPurpose purpose;

    @NotNull(message = "granted is required")
    private Boolean granted;

    @Schema(description = "Free-text tag of the Privacy Notice version shown when this choice was made. "
            + "Optional - not validated against a known list server-side.", example = "privacy-notice-v1-DRAFT")
    private String noticeVersion;
}
