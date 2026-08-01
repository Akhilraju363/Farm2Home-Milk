package com.farm2home.auth.dto.request;

import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.common.core.constants.RegexConstants;
import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = RegexConstants.MOBILE_PATTERN, message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "The mobile number the OTP was sent to.", example = "9876543210")
    private String mobile;

    @NotBlank(message = "OTP is required")
    @Size(min = ValidationConstants.OTP_LENGTH, max = ValidationConstants.OTP_LENGTH, message = "OTP must be 6 digits")
    @Pattern(regexp = RegexConstants.OTP_PATTERN, message = "OTP must contain digits only")
    @Schema(description = "The 6-digit numeric code received by SMS (and email, if applicable). Must match "
            + "the most recently generated, unused OTP for this mobile + otpType within its 5-minute expiry.",
            example = "482913")
    private String otp;

    @NotNull(message = "OTP type is required")
    @Schema(description = "Must match the otpType the OTP was originally requested with.", example = "REGISTRATION")
    private OtpType otpType;
}
