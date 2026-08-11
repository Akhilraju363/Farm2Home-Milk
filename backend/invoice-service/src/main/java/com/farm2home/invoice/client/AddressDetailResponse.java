package com.farm2home.invoice.client;

import lombok.Data;

/** Minimal local projection of customer-service's real AddressResponse. */
@Data
public class AddressDetailResponse {
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private boolean defaultAddress;
}
