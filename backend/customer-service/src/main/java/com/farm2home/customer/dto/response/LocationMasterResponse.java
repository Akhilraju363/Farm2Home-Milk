package com.farm2home.customer.dto.response;

import lombok.Builder;
import lombok.Value;
import java.util.UUID;

@Value @Builder
public class LocationMasterResponse {
    UUID id; String name; String code; boolean active;
    UUID stateId; UUID districtId; String stateName; String districtName;
}
