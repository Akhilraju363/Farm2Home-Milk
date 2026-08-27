package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.dto.response.GoogleAuthResponse;
import com.farm2home.auth.dto.response.OtpVerifyResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void sendOtp(OtpRequest request);

    /** @return registrationRequired=false with auth=null for REGISTRATION/FORGOT_PASSWORD
     *  (unchanged behavior); for LOGIN, auth populated (existing account, signed in) or
     *  registrationRequired=true (no account for this mobile yet - continue registration). */
    OtpVerifyResponse verifyOtp(VerifyOtpRequest request);

    /** Validates the Google credential server-side and either signs the caller into an existing/
     *  just-linked account, or reports registrationRequired=true so the frontend can continue into
     *  the existing registration wizard - see AUTH_SOCIAL_OTP_PROGRESS.md's account-linking policy. */
    GoogleAuthResponse googleAuth(GoogleAuthRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String accessToken);

    /** Maps the already-authenticated principal (resolved by JwtAuthenticationFilter from the
     *  bearer token) to the same UserInfo shape login/register return - lets the frontend
     *  repopulate its in-memory user object (username, roles, etc.) on app boot/page refresh,
     *  since only the token itself, not that object, survives a reload. */
    AuthResponse.UserInfo getCurrentUserInfo(User user);
}
