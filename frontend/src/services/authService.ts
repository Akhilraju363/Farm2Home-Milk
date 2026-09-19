import axiosClient from './authAxiosClient'
import type {
  LoginRequest, RegisterRequest, OtpRequest, OtpType, AuthResponse, UserInfo,
  VerifyOtpResponse, GoogleAuthRequest, GoogleAuthResponse,
} from '../types/auth.types'
import type { ApiResponse } from '../types/common.types'

const AUTH_BASE = '/auth'

export const authService = {
  login: (data: LoginRequest) =>
    axiosClient.post<ApiResponse<AuthResponse>>(`${AUTH_BASE}/login`, data),

  register: (data: RegisterRequest) =>
    axiosClient.post<ApiResponse<AuthResponse>>(`${AUTH_BASE}/register`, data),

  sendOtp: (data: OtpRequest) =>
    axiosClient.post(`${AUTH_BASE}/send-otp`, data),

  // For otpType='LOGIN' this IS Mobile OTP Login: response.data.auth is populated (existing
  // account, signed in) or response.data.registrationRequired is true (no account yet - continue
  // registration, see MobileOtpDialog). For REGISTRATION/FORGOT_PASSWORD, response.data.auth is
  // always undefined - identical observable behavior to before this endpoint's response gained a
  // body.
  verifyOtp: (mobile: string, otp: string, otpType: OtpType) =>
    axiosClient.post<ApiResponse<VerifyOtpResponse>>(`${AUTH_BASE}/verify-otp`, { mobile, otp, otpType }),

  // Validates the Google credential server-side. response.data.auth populated → signed in;
  // response.data.registrationRequired → no Farm2Home account yet, continue into RegisterPage
  // (see LoginPage.tsx's handleGoogleCredentialResponse).
  googleAuth: (data: GoogleAuthRequest) =>
    axiosClient.post<ApiResponse<GoogleAuthResponse>>(`${AUTH_BASE}/google`, data),

  refreshToken: (refreshToken: string) =>
    axiosClient.post<ApiResponse<AuthResponse>>(`${AUTH_BASE}/refresh-token`, { refreshToken }),

  logout: () =>
    axiosClient.post(`${AUTH_BASE}/logout`),

  // Only the token survives a page reload (see authSlice's initialState) - this repopulates the
  // in-memory user object (username, roles, etc.) from it on app boot.
  getMe: () =>
    axiosClient.get<ApiResponse<UserInfo>>(`${AUTH_BASE}/me`),
}
