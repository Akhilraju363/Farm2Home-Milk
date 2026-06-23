package com.farm2home.auth.service;

import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void sendOtp(OtpRequest request);

    void verifyOtp(VerifyOtpRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String accessToken);
}
