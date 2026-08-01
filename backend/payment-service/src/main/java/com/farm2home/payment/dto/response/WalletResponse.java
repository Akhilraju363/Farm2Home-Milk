package com.farm2home.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {

    @Schema(example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID id;

    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;

    @Schema(description = "Current balance in rupees", example = "1250.00")
    private BigDecimal balance;

    @Schema(example = "2026-07-31T10:15:30")
    private LocalDateTime updatedAt;
}
