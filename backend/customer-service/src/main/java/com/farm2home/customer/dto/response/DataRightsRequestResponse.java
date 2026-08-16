package com.farm2home.customer.dto.response;

import com.farm2home.customer.domain.enums.DataRightsRequestStatus;
import com.farm2home.customer.domain.enums.DataRightsRequestType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DataRightsRequestResponse {
    private UUID id;
    private UUID customerId;
    private String requesterName;
    private String requesterContact;
    private DataRightsRequestType requestType;
    private String details;
    private DataRightsRequestStatus status;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
