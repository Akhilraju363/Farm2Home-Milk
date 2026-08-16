package com.farm2home.customer.dto.response;

import com.farm2home.customer.domain.enums.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConsentResponse {

    @Schema(description = "The purpose this consent choice covers.")
    private ConsentPurpose purpose;

    @Schema(description = "Current state - true if the customer has opted in.")
    private boolean granted;

    @Schema(description = "Privacy Notice version in force when this was last set.")
    private String noticeVersion;

    @Schema(description = "When this choice was first recorded.")
    private LocalDateTime createdAt;

    @Schema(description = "When this choice was last changed.")
    private LocalDateTime updatedAt;
}
