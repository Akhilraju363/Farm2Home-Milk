package com.farm2home.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Mirrors only the fields notification-service needs from customer-service's CustomerResponse
 *  (id, customerCode, status, profileImageUrl, createdAt are irrelevant here and ignored). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerContactDto {
    private String firstName;
    private String lastName;
    private String mobile;
    private String email;
}
