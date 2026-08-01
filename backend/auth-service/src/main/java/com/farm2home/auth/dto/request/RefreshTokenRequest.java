package com.farm2home.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "A refresh token previously issued by /login, /register, or a prior call to this "
            + "endpoint. Single-use — it is revoked as soon as it's redeemed here.",
            example = "8b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e.def456")
    private String refreshToken;
}
