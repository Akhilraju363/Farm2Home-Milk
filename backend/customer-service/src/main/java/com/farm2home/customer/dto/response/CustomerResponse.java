package com.farm2home.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {
    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;
    @Schema(example = "CUST-00042")
    private String customerCode;
    @Schema(example = "Asha")
    private String firstName;
    @Schema(example = "Rao")
    private String lastName;
    @Schema(example = "9876543210")
    private String mobile;
    @Schema(example = "asha.rao@example.com")
    private String email;
    @Schema(description = "CustomerStatus enum name", example = "ACTIVE")
    private String status;
    @Schema(description = "Null until a profile image has been uploaded via POST /{id}/profile-image",
            example = "/uploads/customers/8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b.jpg")
    private String profileImageUrl;
    @Schema(example = "2026-06-15T09:20:00")
    private LocalDateTime createdAt;
}
