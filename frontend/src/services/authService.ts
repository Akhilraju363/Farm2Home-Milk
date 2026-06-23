import axiosClient from './axiosClient'
import type { LoginRequest, RegisterRequest, OtpRequest, AuthResponse } from '../types/auth.types'

const AUTH_BASE = '/auth'

export const authService = {
  login: (data: LoginRequest) =>
    axiosClient.post<AuthResponse>(`${AUTH_BASE}/login`, data),

  register: (data: RegisterRequest) =>
    axiosClient.post(`${AUTH_BASE}/register`, data),

  sendOtp: (data: OtpRequest) =>
    axiosClient.post(`${AUTH_BASE}/send-otp`, data),

  verifyOtp: (mobile: string, otp: string) =>
    axiosClient.post(`${AUTH_BASE}/verify-otp`, { mobile, otp }),

  refreshToken: (refreshToken: string) =>
    axiosClient.post<AuthResponse>(`${AUTH_BASE}/refresh-token`, { refreshToken }),

  logout: () =>
    axiosClient.post(`${AUTH_BASE}/logout`),
}
