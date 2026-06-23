package com.farm2home.delivery.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdatePartnerRequest {
    private UUID routeId;
    private String name;
    private String mobile;
    private String vehicleType;
    private Boolean active;
}
