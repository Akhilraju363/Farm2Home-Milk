package com.farm2home.delivery.dto.request;

import lombok.Data;

@Data
public class UpdateRouteRequest {
    private String routeName;
    private String area;
    private String city;
    private String pincode;
    private Boolean active;
}
