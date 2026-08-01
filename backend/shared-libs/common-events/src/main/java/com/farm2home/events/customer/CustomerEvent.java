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
    private String customerName;
    private String mobile;
    private String email;
    private LocalDateTime occurredAt;
}
