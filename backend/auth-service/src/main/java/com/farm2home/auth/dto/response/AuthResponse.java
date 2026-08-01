package com.farm2home.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    @Schema(description = "Short-lived JWT to send as `Authorization: Bearer <accessToken>` on subsequent "
            + "authenticated requests.", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ODc2NTQzMjEwIn0.abc123")
    private String accessToken;

    @Schema(description = "Longer-lived, single-use token accepted by POST /auth/refresh-token to obtain a "
            + "new token pair. Revoked once redeemed, or when the user logs in again or calls /auth/logout.",
            example = "8b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e.def456")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Authorization header scheme to use with accessToken.", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Seconds until accessToken/refreshToken expire (mirrors jwt.refresh-expiration).",
            example = "604800")
    private long expiresIn;

    private UserInfo user;

    @Data
    @Builder
    @Schema(description = "Snapshot of the authenticated user's profile at the time of login/registration/refresh.")
    public static class UserInfo {
        @Schema(example = "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d")
        private UUID id;
        @Schema(example = "9876543210")
        private String mobile;
        @Schema(example = "jane.doe@example.com")
        private String email;
        @Schema(example = "jane.doe")
        private String username;
        @Schema(description = "Whether the account's mobile number has been OTP-verified.", example = "true")
        private boolean verified;
        @Schema(example = "[\"CUSTOMER\"]")
        private List<String> roles;
    }
}
