const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

/** Auth tokens can live in localStorage (persists across browser restarts - "Remember Me") or
 *  sessionStorage (cleared when the tab closes). Only one of the two ever holds the live tokens
 *  at a time - setTokens always clears the other, so reads never need to worry about stale
 *  leftovers from a previous choice. */
export const tokenStorage = {
  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY) ?? sessionStorage.getItem(ACCESS_KEY)
  },

  setTokens(accessToken: string, refreshToken: string, remember: boolean): void {
    const target = remember ? localStorage : sessionStorage
    const other = remember ? sessionStorage : localStorage
    target.setItem(ACCESS_KEY, accessToken)
    target.setItem(REFRESH_KEY, refreshToken)
    other.removeItem(ACCESS_KEY)
    other.removeItem(REFRESH_KEY)
  },

  clear(): void {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    sessionStorage.removeItem(ACCESS_KEY)
    sessionStorage.removeItem(REFRESH_KEY)
  },
}
