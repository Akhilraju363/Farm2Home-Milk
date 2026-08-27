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
  // Present only when this registration continues a Google Sign-In that found no existing
  // account (see GoogleAuthResponse.registrationRequired) - the same Google ID token, re-validated
  // server-side. Omitted entirely for a normal password-only registration.
  googleCredential?: string
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

export interface VerifyOtpResponse {
  registrationRequired: boolean
  // Populated only when registrationRequired=false and an account actually exists (i.e. for
  // otpType=LOGIN) - identical shape to AuthResponse from POST /login. Absent/undefined for
  // REGISTRATION/FORGOT_PASSWORD, whose verify-otp call never signs anyone in.
  auth?: AuthResponse
}

export interface GoogleAuthRequest {
  // The ID token from Google Identity Services' credential response - see LoginPage.tsx's
  // handleGoogleCredentialResponse. Never a plain email/name assembled client-side.
  credential: string
}

export interface GoogleAuthResponse {
  registrationRequired: boolean
  auth?: AuthResponse
  // Populated only when registrationRequired=true, sourced server-side from the validated Google
  // credential - safe to pre-fill the registration form with, never editable-then-resubmitted as
  // if it were re-verified.
  firstName?: string
  lastName?: string
  email?: string
}
