package com.farm2home.events.customer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published to {@code customer.events} whenever a customer's identity/profile changes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerEvent {

    /** CUSTOMER_CREATED */
    private String eventType;
    private UUID customerId;
    // Kept for consumers that only need a display name (e.g. notification-service's welcome
    // email template) - customer-service itself uses firstName/lastName below instead of
    // splitting this, so registering with a multi-word last name round-trips exactly.
    private String customerName;
    private String firstName;
    private String lastName;
    private String mobile;
    private String email;
    private LocalDateTime occurredAt;
}
