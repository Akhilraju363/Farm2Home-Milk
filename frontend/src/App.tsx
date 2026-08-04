import { useEffect, useState } from 'react'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { theme } from './utils/theme'
import { AppRoutes } from './routes/AppRoutes'
import { LoadingScreen } from './components/common/LoadingScreen'

const SPLASH_DURATION_MS = 1200

function App() {
  const [isBooting, setIsBooting] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => setIsBooting(false), SPLASH_DURATION_MS)
    return () => clearTimeout(timer)
  }, [])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {isBooting ? <LoadingScreen /> : <AppRoutes />}
    </ThemeProvider>
  )
}

export default App
