import { createSlice } from '@reduxjs/toolkit'
import type { UserInfo } from '../../types/auth.types'
import { tokenStorage } from '../../services/tokenStorage'

interface AuthState {
  user: UserInfo | null
  accessToken: string | null
  isAuthenticated: boolean
  loading: boolean
}

const initialState: AuthState = {
  user: null,
  accessToken: tokenStorage.getAccessToken(),
  isAuthenticated: !!tokenStorage.getAccessToken(),
  loading: false,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    // Only updates in-memory state. Callers are responsible for persisting tokens themselves via
    // tokenStorage.setTokens(...) beforehand, since only the caller (LoginPage) knows whether the
    // user asked to be remembered (localStorage) or not (sessionStorage).
    setCredentials(state, action) {
      state.user = action.payload.user
      state.accessToken = action.payload.accessToken
      state.isAuthenticated = true
    },
    logout(state) {
      state.user = null
      state.accessToken = null
      state.isAuthenticated = false
      tokenStorage.clear()
    },
  },
})

export const { setCredentials, logout } = authSlice.actions
export default authSlice.reducer
