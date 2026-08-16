package com.farm2home.customer.dto.request;

import com.farm2home.customer.domain.enums.DataRightsRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDataRightsRequestStatusRequest {

    @NotNull(message = "Status is required")
    private DataRightsRequestStatus status;

    @Size(max = 4000, message = "Resolution notes must be at most 4000 characters")
    private String resolutionNotes;
}
