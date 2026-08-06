import axiosClient from './axiosClient'
import type { LoginRequest, RegisterRequest, OtpRequest, OtpType, AuthResponse, UserInfo } from '../types/auth.types'
import type { ApiResponse } from '../types/common.types'

const AUTH_BASE = '/auth'

export const authService = {
  login: (data: LoginRequest) =>
    axiosClient.post<ApiResponse<AuthResponse>>(`${AUTH_BASE}/login`, data),

  register: (data: RegisterRequest) =>
    axiosClient.post<ApiResponse<AuthResponse>>(`${AUTH_BASE}/register`, data),

  sendOtp: (data: OtpRequest) =>
    axiosClient.post(`${AUTH_BASE}/send-otp`, data),

  verifyOtp: (mobile: string, otp: string, otpType: OtpType) =>
    axiosClient.post(`${AUTH_BASE}/verify-otp`, { mobile, otp, otpType }),

  refreshToken: (refreshToken: string) =>
    axiosClient.post<ApiResponse<AuthResponse>>(`${AUTH_BASE}/refresh-token`, { refreshToken }),

  logout: () =>
    axiosClient.post(`${AUTH_BASE}/logout`),

  // Only the token survives a page reload (see authSlice's initialState) - this repopulates the
  // in-memory user object (username, roles, etc.) from it on app boot.
  getMe: () =>
    axiosClient.get<ApiResponse<UserInfo>>(`${AUTH_BASE}/me`),
}
