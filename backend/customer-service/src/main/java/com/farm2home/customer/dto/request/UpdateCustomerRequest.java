package com.farm2home.customer.dto.request;

import com.farm2home.customer.domain.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial update - every field is optional and left null (unchanged) on the entity if omitted.
 * Immutable fields such as mobile and customerCode are deliberately not present here.
 */
@Data
public class UpdateCustomerRequest {

    @Schema(description = "Leave null to keep the existing first name", example = "Asha", minLength = 2, maxLength = 100)
    @Size(min = 2, max = 100)
    private String firstName;

    @Schema(description = "Leave null to keep the existing last name", example = "Rao", minLength = 2, maxLength = 100)
    @Size(min = 2, max = 100)
    private String lastName;

    @Schema(description = "Leave null to keep the existing email", example = "asha.rao@example.com")
    @Email(message = "Enter a valid email address")
    private String email;

    @Schema(description = "Leave null to keep the existing status. Deleting a customer requires "
            + "first setting this to INACTIVE or SUSPENDED (see DELETE /{id}).", example = "INACTIVE")
    private CustomerStatus status;
}
