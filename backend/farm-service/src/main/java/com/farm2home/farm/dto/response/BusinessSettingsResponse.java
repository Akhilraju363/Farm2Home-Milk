package com.farm2home.farm.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BusinessSettingsResponse {
    @Schema(description = "Farm2Home's own delivery-origin latitude.", example = "13.62708825")
    private BigDecimal farmLatitude;
    @Schema(description = "Farm2Home's own delivery-origin longitude.", example = "78.96885066")
    private BigDecimal farmLongitude;
    @Schema(description = "Maximum delivery distance from the origin, in kilometres.", example = "10.00")
    private BigDecimal deliveryRadiusKm;

    @Schema(description = "Farm2Home's real business address - display-only, not used in the "
            + "delivery-eligibility calculation (see farmLatitude/farmLongitude). The single "
            + "authoritative source for this text.", example = "Farm2Home")
    private String businessName;
    @Schema(example = "ST Colony")
    private String addressLine;
    @Schema(example = "Yerraguntlapalle")
    private String locality;
    @Schema(example = "Pileru")
    private String city;
    @Schema(example = "Chittoor")
    private String district;
    @Schema(example = "Andhra Pradesh")
    private String state;
    @Schema(example = "517214")
    private String pincode;

    private LocalDateTime updatedAt;
}
