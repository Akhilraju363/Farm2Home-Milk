package com.farm2home.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AddressResponse {
    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;
    @Schema(example = "402, Block A, Maple Avenue")
    private String addressLine1;
    @Schema(example = "Green Hills Colony")
    private String addressLine2;
    @Schema(example = "Mumbai")
    private String city;
    @Schema(example = "Maharashtra")
    private String state;
    @Schema(example = "400001")
    private String pincode;
    private boolean defaultAddress;
    @Schema(example = "2026-06-15T09:20:00")
    private LocalDateTime createdAt;
}
