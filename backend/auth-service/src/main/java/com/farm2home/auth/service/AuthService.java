package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void sendOtp(OtpRequest request);

    void verifyOtp(VerifyOtpRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String accessToken);

    /** Maps the already-authenticated principal (resolved by JwtAuthenticationFilter from the
     *  bearer token) to the same UserInfo shape login/register return - lets the frontend
     *  repopulate its in-memory user object (username, roles, etc.) on app boot/page refresh,
     *  since only the token itself, not that object, survives a reload. */
    AuthResponse.UserInfo getCurrentUserInfo(User user);
}
