package com.farm2home.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LocationStateResponse {
    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;
    @Schema(example = "Andhra Pradesh")
    private String name;
    @Schema(example = "AP")
    private String code;
}
