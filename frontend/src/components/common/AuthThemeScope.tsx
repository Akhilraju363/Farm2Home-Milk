import { Outlet } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { getTheme } from '../../utils/theme'

const lightTheme = getTheme('light')

/** Login/Register/ForgotPassword use a fixed, hero-image-branded light design (hardcoded
 *  backgrounds like #f7faf8/#eef3f1, not theme tokens) - but their text/label colors are left to
 *  inherit from MUI's ambient palette, which flips to near-white in dark mode. A user with dark
 *  mode active (toggled previously, or via OS prefers-color-scheme on a first visit with no
 *  stored preference) would see near-invisible text on these pages. Force light mode for just
 *  this route subtree instead, rather than for the main app, which supports dark mode correctly.
 *
 *  The nested CssBaseline is required, not redundant with the outer one from ThemeModeProvider:
 *  CssBaseline sets `body`'s own color/background, and any Typography without an explicit
 *  `color` prop (e.g. "Welcome Back") relies on CSS inheritance from body rather than setting its
 *  own color - without a second CssBaseline scoped after the outer one, that inherited color
 *  would still come from the app-wide (possibly dark) theme. */
export function AuthThemeScope() {
  return (
    <ThemeProvider theme={lightTheme}>
      <CssBaseline />
      <Outlet />
    </ThemeProvider>
  )
}
