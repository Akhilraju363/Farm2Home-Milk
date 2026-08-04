package com.farm2home.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Mobile number, email, or username is required")
    @Schema(description = "Login identifier - the 10-digit Indian mobile number, the registered "
            + "email address, or the account's username (see "
            + "UserRepository.findByIdentifierAndDeletedFalse for how the three are told apart). "
            + "No format is validated here since all three are legal; an unrecognized identifier "
            + "simply fails authentication like a wrong password would.",
            example = "9876543210")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password, checked against the stored BCrypt hash.", example = "Str0ng!Pass")
    private String password;
}
