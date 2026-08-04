package com.farm2home.auth.dto.request;

import com.farm2home.auth.domain.enums.OtpType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OtpRequest {

    @NotBlank(message = "Mobile number or email is required")
    @Schema(description = "Either the 10-digit Indian mobile number to send the OTP to directly, or a "
            + "registered email address to resolve to its account's mobile number first (see "
            + "AuthServiceImpl.sendOtp). For otpType=REGISTRATION the mobile need not already belong to a "
            + "registered user; for LOGIN/FORGOT_PASSWORD an email that doesn't match any account silently "
            + "sends nothing (never reveals whether the email is registered).", example = "9876543210")
    private String identifier;

    @NotNull(message = "OTP type is required")
    @Schema(description = "Purpose of the OTP: REGISTRATION (consumed by verify-otp to activate a new "
            + "account), LOGIN, or FORGOT_PASSWORD.", example = "REGISTRATION")
    private OtpType otpType;
}
