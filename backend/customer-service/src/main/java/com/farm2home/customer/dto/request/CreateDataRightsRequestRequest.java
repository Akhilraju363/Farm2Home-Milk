package com.farm2home.customer.dto.request;

import com.farm2home.customer.domain.enums.DataRightsRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "A DPDP data-rights request or grievance. Intentionally does not require the "
        + "submitter to be logged in - a data principal without an account (e.g. objecting to being "
        + "contacted) must still be able to reach this channel.")
public class CreateDataRightsRequestRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String requesterName;

    @NotBlank(message = "A contact email or mobile number is required")
    @Size(max = 150, message = "Contact must be at most 150 characters")
    @Schema(description = "Email or mobile - whichever the requester wants to be reached on. Not "
            + "validated as a strict email/mobile format, since either is acceptable.", example = "akhil@example.com")
    private String requesterContact;

    @NotNull(message = "Request type is required")
    private DataRightsRequestType requestType;

    @Size(max = 4000, message = "Details must be at most 4000 characters")
    private String details;
}
