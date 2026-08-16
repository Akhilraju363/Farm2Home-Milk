package com.farm2home.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class LocationMasterRequest {
    @NotBlank @Size(max = 100) private String name;
    @Size(max = 20) private String code;
    private UUID stateId;
    private UUID districtId;
    @NotNull private Boolean active;
}
