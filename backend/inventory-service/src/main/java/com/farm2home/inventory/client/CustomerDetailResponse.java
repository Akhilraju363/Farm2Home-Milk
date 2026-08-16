package com.farm2home.inventory.client;

import lombok.Data;

import java.util.UUID;

/** Minimal local projection of customer-service's real CustomerResponse - only the fields a
 *  review's "reviewed by" display needs. Mirrors invoice-service's CustomerDetailResponse. */
@Data
public class CustomerDetailResponse {
    private UUID id;
    private String firstName;
    private String lastName;
}
