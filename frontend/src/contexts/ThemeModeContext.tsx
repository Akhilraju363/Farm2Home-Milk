import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { ThemeProvider } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import type { PaletteMode } from '@mui/material'
import { useSelector } from 'react-redux'
import { getTheme } from '../utils/theme'
import { getUserTheme, setUserTheme } from '../utils/userTheme'
import type { RootState } from '../store/store'

const DEFAULT_MODE: PaletteMode = 'light'

// No user -> always the app default, never a leftover preference from whoever was last logged
// in (see userTheme.ts - there is no unscoped/global theme key to fall back to at all anymore).
function resolveMode(userId: string | null): PaletteMode {
  return userId ? (getUserTheme(userId) ?? DEFAULT_MODE) : DEFAULT_MODE
}

const ThemeModeContext = createContext<{ mode: PaletteMode; toggleMode: () => void } | null>(null)

export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const userId = useSelector((state: RootState) => state.auth.user?.id) ?? null

  // Re-derive mode synchronously during render (not in a useEffect) whenever the authenticated
  // user changes, so switching accounts - e.g. SUPER_ADMIN (dark) logging out and CUSTOMER (light)
  // logging in - never paints a frame with the previous account's theme first. This is React's
  // documented "adjusting state when a prop changes" pattern precisely because it replaces the
  // in-progress render before anything commits to the DOM, unlike an effect which would run one
  // paint too late and cause the exact flash this needs to avoid.
  //
  // Tracking the previous userId with useState (not useRef): a ref mutated directly during render
  // is only safe for one-time lazy init - React.StrictMode (enabled in main.tsx) intentionally
  // double-invokes component bodies in development, so a ref write on the first, discarded
  // invocation would already have "consumed" the change before the second, real invocation runs
  // its own comparison, silently skipping the reset. useState's setter is safe to call during
  // render precisely because React re-runs the *whole* render with the new state before either
  // invocation commits, so double-invocation can't cause it to be missed.
  const [prevUserId, setPrevUserId] = useState(userId)
  const [mode, setMode] = useState<PaletteMode>(() => resolveMode(userId))
  if (userId !== prevUserId) {
    setPrevUserId(userId)
    setMode(resolveMode(userId))
  }

  const toggleMode = () => {
    setMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light'
      // Persisted only under the current user's own key - toggling never writes a global/shared
      // preference, and there's nothing to persist for an unauthenticated viewer (toggleMode is
      // only reachable from TopBar, which only renders once logged in, but this stays defensive).
      if (userId) setUserTheme(userId, next)
      return next
    })
  }

  const theme = useMemo(() => getTheme(mode), [mode])

  return (
    <ThemeModeContext.Provider value={{ mode, toggleMode }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  )
}

export function useThemeMode() {
  const ctx = useContext(ThemeModeContext)
  if (!ctx) throw new Error('useThemeMode must be used within ThemeModeProvider')
  return ctx
}
