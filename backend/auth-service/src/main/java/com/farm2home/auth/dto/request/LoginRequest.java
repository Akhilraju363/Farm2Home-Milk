package com.farm2home.auth.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = RegexConstants.MOBILE_PATTERN, message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "10-digit Indian mobile number used as the login identifier.", example = "9876543210")
    private String mobile;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password, checked against the stored BCrypt hash.", example = "Str0ng!Pass")
    private String password;
}
