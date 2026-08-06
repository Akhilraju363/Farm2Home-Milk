import { createTheme } from '@mui/material/styles'
import type { PaletteMode } from '@mui/material'

export function getTheme(mode: PaletteMode) {
  return createTheme({
    palette: {
      mode,
      primary: {
        main: '#2E7D32',
        light: '#4CAF50',
        dark: '#1B5E20',
      },
      secondary: {
        main: '#F9A825',
        light: '#FDD835',
        dark: '#F57F17',
      },
      ...(mode === 'light'
        ? {
            background: { default: '#F5F5F5', paper: '#FFFFFF' },
          }
        : {
            background: { default: '#121212', paper: '#1E1E1E' },
          }),
    },
    typography: {
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    },
    shape: {
      borderRadius: 8,
    },
  })
}
