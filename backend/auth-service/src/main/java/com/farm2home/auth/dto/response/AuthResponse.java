package com.farm2home.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private UUID id;
        private String mobile;
        private String email;
        private String username;
        private boolean verified;
        private List<String> roles;
    }
}
