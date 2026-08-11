package com.farm2home.invoice.client;

import lombok.Data;

import java.util.UUID;

/** Minimal local projection of customer-service's real CustomerResponse - only the fields an
 *  invoice's customer header renders. */
@Data
public class CustomerDetailResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String mobile;
    private String email;
}
