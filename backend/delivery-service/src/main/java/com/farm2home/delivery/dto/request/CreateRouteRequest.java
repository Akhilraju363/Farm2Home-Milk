package com.farm2home.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRouteRequest {

    @NotBlank(message = "Route name is required")
    @Size(max = 100, message = "Route name must be at most 100 characters")
    private String routeName;

    @NotBlank(message = "Route code is required")
    @Size(max = 20, message = "Route code must be at most 20 characters")
    private String routeCode;

    @NotBlank(message = "Area is required")
    private String area;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Pincode is required")
    @Size(min = 6, max = 10, message = "Pincode must be between 6 and 10 characters")
    private String pincode;
}
