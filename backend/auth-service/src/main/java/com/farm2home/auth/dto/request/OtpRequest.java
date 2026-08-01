package com.farm2home.auth.dto.request;

import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.common.core.constants.RegexConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = RegexConstants.MOBILE_PATTERN, message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "10-digit Indian mobile number to send the OTP to. Need not already belong to a "
            + "registered user.", example = "9876543210")
    private String mobile;

    @NotNull(message = "OTP type is required")
    @Schema(description = "Purpose of the OTP: REGISTRATION (consumed by verify-otp to activate a new "
            + "account), LOGIN, or FORGOT_PASSWORD.", example = "REGISTRATION")
    private OtpType otpType;
}
