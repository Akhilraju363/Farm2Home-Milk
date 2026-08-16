import { useEffect, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { ThemeModeProvider } from './contexts/ThemeModeContext'
import { AppRoutes } from './routes/AppRoutes'
import { LoadingScreen } from './components/common/LoadingScreen'
import { ConsentBanner } from './components/consent/ConsentBanner'
import { authService } from './services/authService'
import { setCredentials } from './store/slices/authSlice'
import type { AppDispatch, RootState } from './store/store'

const SPLASH_DURATION_MS = 1200

function App() {
  const [isBooting, setIsBooting] = useState(true)
  const dispatch = useDispatch<AppDispatch>()
  const { accessToken, user } = useSelector((state: RootState) => state.auth)

  useEffect(() => {
    const timer = setTimeout(() => setIsBooting(false), SPLASH_DURATION_MS)
    return () => clearTimeout(timer)
  }, [])

  // Only the token survives a page reload (see authSlice's initialState: user always starts
  // null) - repopulate the user object from it here so isAdmin()/username/etc. work immediately
  // instead of silently reading as "not admin"/blank until the next fresh login. A failed call
  // (expired/invalid token) is left to axiosClient's 401 interceptor, which already redirects to
  // /login and clears storage.
  useEffect(() => {
    if (accessToken && !user) {
      authService.getMe()
        .then((res) => dispatch(setCredentials({ accessToken, user: res.data.data })))
        .catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, user])

  return (
    <ThemeModeProvider>
      {isBooting ? <LoadingScreen /> : (
        <>
          <AppRoutes />
          <ConsentBanner />
        </>
      )}
    </ThemeModeProvider>
  )
}

export default App
