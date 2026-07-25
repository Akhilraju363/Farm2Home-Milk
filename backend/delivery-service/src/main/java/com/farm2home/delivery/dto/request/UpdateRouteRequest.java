package com.farm2home.delivery.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRouteRequest {

    @Size(max = 100, message = "Route name must be at most 100 characters")
    private String routeName;

    @Size(max = 100, message = "Area must be at most 100 characters")
    private String area;

    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @Size(min = 6, max = 10, message = "Pincode must be between 6 and 10 characters")
    private String pincode;
    private Boolean active;
}
