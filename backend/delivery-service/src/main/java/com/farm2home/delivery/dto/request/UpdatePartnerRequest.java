package com.farm2home.delivery.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdatePartnerRequest {
    private UUID routeId;

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
    private String mobile;

    @Size(max = 50, message = "Vehicle type must be at most 50 characters")
    private String vehicleType;
    private Boolean active;
}
