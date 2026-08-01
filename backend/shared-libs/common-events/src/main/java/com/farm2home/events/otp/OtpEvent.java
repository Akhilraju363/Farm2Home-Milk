package com.farm2home.events.otp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published to {@code otp.events} whenever an OTP is generated for a user with an email
 *  on file - SMS delivery of the OTP remains auth-service's own synchronous concern. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpEvent {

    /** OTP */
    private String eventType;
    private UUID customerId;
    private String customerName;
    private String mobile;
    private String email;
    private String otp;
    private LocalDateTime occurredAt;
}
