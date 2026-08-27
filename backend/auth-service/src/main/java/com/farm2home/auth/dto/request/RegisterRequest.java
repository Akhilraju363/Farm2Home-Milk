package com.farm2home.auth.dto.request;

import com.farm2home.common.core.constants.RegexConstants;
import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be 2–50 characters")
    @Schema(description = "Customer's first name. 2–50 characters.", example = "Jane")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be 2–50 characters")
    @Schema(description = "Customer's last name. 2–50 characters.", example = "Doe")
    private String lastName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = RegexConstants.MOBILE_PATTERN, message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "10-digit Indian mobile number, without country code. Must not already be "
            + "registered. This is also the login identifier and where the verification OTP is sent.",
            example = "9876543210")
    private String mobile;

    @Email(message = "Enter a valid email address")
    @Schema(description = "Optional email address. If provided, must not already be registered. Used as a "
            + "secondary channel for OTP delivery on future send-otp calls.", example = "jane.doe@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = ValidationConstants.PASSWORD_MIN_LENGTH, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = RegexConstants.PASSWORD_PATTERN,
        message = "Password must contain uppercase, lowercase, digit, and special character"
    )
    @Schema(description = "At least 8 characters, containing at least one uppercase letter, one lowercase "
            + "letter, one digit, and one special character. Stored only as a BCrypt hash.",
            example = "Str0ng!Pass")
    private String password;

    @Schema(description = "Optional - present only when this registration continues a Google Sign-In "
            + "that found no existing Farm2Home account (see POST /auth/google's "
            + "registrationRequired=true response). The same Google ID token is re-validated "
            + "server-side here (see GoogleTokenValidator); if present, its email must match the "
            + "email field above exactly, and the resulting account is immediately linked to that "
            + "Google identity. Omit entirely for a normal password-only registration - this changes "
            + "nothing about that path.")
    private String googleCredential;
}
