export interface LoginRequest {
  identifier: string
  password: string
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  mobile: string
  email?: string
  password: string
}

export type OtpType = 'REGISTRATION' | 'LOGIN' | 'FORGOT_PASSWORD'

export interface OtpRequest {
  identifier: string
  otpType: OtpType
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface UserInfo {
  id: string
  username: string
  mobile: string
  email?: string
  roles: Role[]
}

export type Role =
  | 'SUPER_ADMIN'
  | 'FARM_MANAGER'
  | 'DELIVERY_MANAGER'
  | 'DELIVERY_PARTNER'
  | 'CUSTOMER'
