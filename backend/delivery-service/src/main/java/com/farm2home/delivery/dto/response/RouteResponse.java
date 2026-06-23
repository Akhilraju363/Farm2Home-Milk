package com.farm2home.delivery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RouteResponse {
    private UUID id;
    private String routeName;
    private String routeCode;
    private String area;
    private String city;
    private String pincode;
    private boolean active;
    private LocalDateTime createdAt;
}
