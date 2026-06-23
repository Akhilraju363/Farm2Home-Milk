package com.farm2home.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePartnerRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    private UUID routeId;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Mobile is required")
    private String mobile;

    private String vehicleType;
}
