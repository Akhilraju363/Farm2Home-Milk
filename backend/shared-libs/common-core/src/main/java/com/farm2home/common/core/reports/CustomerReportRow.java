package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the Customer Report (customer-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerReportRow {
    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;
    @Schema(example = "CUST-00042")
    private String customerCode;
    @Schema(example = "Asha Rao")
    private String name;
    @Schema(example = "9876543210")
    private String mobile;
    @Schema(example = "asha.rao@example.com")
    private String email;
    @Schema(description = "CustomerStatus enum name", example = "ACTIVE")
    private String status;
    @Schema(example = "2026-06-15T09:20:00")
    private LocalDateTime createdAt;
}
