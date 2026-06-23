package com.farm2home.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {
    private UUID id;
    private UUID customerId;
    private BigDecimal balance;
    private LocalDateTime updatedAt;
}
