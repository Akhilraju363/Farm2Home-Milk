package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the Delivery Report (delivery-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryReportRow {
    @Schema(example = "8f90a1b2-6d7e-4c3f-9a4b-5c6d7e8f90a1")
    private UUID assignmentId;
    @Schema(example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID orderId;
    @Schema(example = "a1b2c3d4-9a4b-4c3f-8d7e-6f90a1b2c3d4")
    private UUID deliveryPartnerId;
    @Schema(description = "delivery-service AssignmentStatus enum name", example = "DELIVERED")
    private String status;
    @Schema(example = "2026-07-20T06:00:00")
    private LocalDateTime assignedAt;
    @Schema(description = "Null until the assignment reaches a terminal status", example = "2026-07-20T08:45:00")
    private LocalDateTime deliveredAt;
}
