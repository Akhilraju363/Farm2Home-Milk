package com.farm2home.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartnerResponse {
    private UUID id;
    private UUID userId;
    private UUID routeId;
    private String routeCode;
    private String name;
    private String mobile;
    private String vehicleType;
    private boolean active;
    private LocalDateTime createdAt;
}
