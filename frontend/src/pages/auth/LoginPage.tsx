import {
  Box, TextField, Button, Typography, Divider, Checkbox, FormControlLabel,
  InputAdornment, IconButton, CircularProgress, Alert, Tooltip,
} from '@mui/material'
import { Visibility, VisibilityOff, Person, Lock, Sms, Agriculture } from '@mui/icons-material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useLocation, useNavigate, Link as RouterLink } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import loginHeroImage from '../../assets/images/login-hero.png'
import type { AppDispatch } from '../../store/store'
import { setCredentials } from '../../store/slices/authSlice'
import { authService } from '../../services/authService'
import { tokenStorage } from '../../services/tokenStorage'

const schema = yup.object({
  // No format restriction here to match the backend (LoginRequest identifier is @NotBlank only):
  // it accepts a mobile number, an email, or a plain username (e.g. staff/admin accounts) - see
  // UserRepository.findByIdentifierAndDeletedFalse for the resolution order.
  identifier: yup.string().required('Mobile number, email, or username is required'),
  password: yup.string().min(6, 'Min 6 characters').required('Password is required'),
})

type FormData = yup.InferType<typeof schema>

const fieldSx = {
  '& .MuiOutlinedInput-root': {
    bgcolor: '#eef3f1',
    borderRadius: 2,
    '& fieldset': { borderColor: 'transparent' },
    '&:hover fieldset': { borderColor: 'transparent' },
    '&.Mui-focused fieldset': { borderColor: 'primary.main' },
  },
}

/** MUI's own `Google` icon is a flat monochrome glyph, and Button's disabled state washes any
 *  inherited `color` out further - hardcoding each path's fill (instead of the default
 *  `currentColor`) keeps the real brand colors visible even inside a disabled button. */
function GoogleLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden>
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.87 2.7-6.62z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.81.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.96v2.33A9 9 0 0 0 9 18z" />
      <path fill="#FBBC05" d="M3.95 10.7A5.4 5.4 0 0 1 3.67 9c0-.59.1-1.17.28-1.7V4.97H.96A9 9 0 0 0 0 9c0 1.45.35 2.83.96 4.03l2.99-2.33z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .96 4.97l2.99 2.33C4.66 5.17 6.65 3.58 9 3.58z" />
    </svg>
  )
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch<AppDispatch>()
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)
  const [error, setError] = useState('')
  const justRegistered = Boolean((location.state as { registered?: boolean } | null)?.registered)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: yupResolver(schema),
  })

  const onSubmit = async (data: FormData) => {
    setError('')
    try {
      const res = await authService.login({ identifier: data.identifier, password: data.password })
      const { accessToken, refreshToken, user } = res.data.data
      tokenStorage.setTokens(accessToken, refreshToken, rememberMe)
      dispatch(setCredentials({ accessToken, user }))
      navigate('/dashboard', { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Invalid credentials. Please try again.')
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: { xs: 'column', md: 'row' } }}>
      <Box
        sx={{
          flex: 1,
          minHeight: { xs: 240, md: '100vh' },
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 4,
          color: '#fff',
          backgroundImage: `linear-gradient(180deg, rgba(20,35,22,0.55) 0%, rgba(20,35,22,0.1) 30%, rgba(20,35,22,0.05) 55%, rgba(15,28,18,0.9) 100%), url(${loginHeroImage})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Agriculture />
          <Typography variant="h6" fontWeight={700}>Farm2Home</Typography>
        </Box>

        <Box>
          <Typography variant="h4" fontWeight={700} lineHeight={1.25}>
            Farm Freshness,<br />Delivered to Your Doorstep.
          </Typography>
          <Typography variant="body2" sx={{ mt: 1.5, opacity: 0.9, maxWidth: 380 }}>
            Experience the purity of farm-to-table dairy products, sourced sustainably and delivered daily.
          </Typography>
        </Box>
      </Box>

      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#f7faf8', p: 3 }}>
        <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ width: '100%', maxWidth: 400 }}>
          <Typography variant="h5" fontWeight={700} mb={0.5}>Welcome Back</Typography>
          <Typography variant="body2" color="text.secondary" mb={3}>
            Please enter your details to sign in.
          </Typography>

          {justRegistered && !error && (
            <Alert severity="success" sx={{ mb: 2 }}>
              Registration successful. We sent a verification code to your mobile — you can log in now.
            </Alert>
          )}
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          <TextField
            label="Mobile Number or Email"
            fullWidth
            margin="normal"
            sx={fieldSx}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Person fontSize="small" color="action" />
                </InputAdornment>
              ),
            }}
            error={!!errors.identifier}
            helperText={errors.identifier?.message}
            {...register('identifier')}
          />

          <TextField
            label="Password"
            type={showPassword ? 'text' : 'password'}
            fullWidth
            margin="normal"
            sx={fieldSx}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Lock fontSize="small" color="action" />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowPassword(!showPassword)} edge="end" size="small">
                    {showPassword ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
            error={!!errors.password}
            helperText={errors.password?.message}
            {...register('password')}
          />

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 0.5 }}>
            <FormControlLabel
              control={
                <Checkbox
                  size="small"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
              }
              label={<Typography variant="body2">Remember Me</Typography>}
            />
            <Typography component={RouterLink} to="/forgot-password" variant="body2" color="primary.main" fontWeight={700} sx={{ textDecoration: 'none' }}>
              Forgot Password?
            </Typography>
          </Box>

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            disabled={isSubmitting}
            sx={{ mt: 2, mb: 2, py: 1.5 }}
          >
            {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Login to Account'}
          </Button>

          <Divider sx={{ my: 2 }}>
            <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: 0.5 }}>
              OR CONTINUE WITH
            </Typography>
          </Divider>

          <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
            <Tooltip title="Google sign-in isn't available yet">
              <span style={{ flex: 1 }}>
                <Button fullWidth variant="outlined" startIcon={<GoogleLogo />} disabled>
                  Google
                </Button>
              </span>
            </Tooltip>
            <Tooltip title="Mobile OTP sign-in isn't available yet">
              <span style={{ flex: 1 }}>
                <Button
                  fullWidth
                  variant="outlined"
                  startIcon={<Sms fontSize="small" sx={{ color: '#0b57d0 !important' }} />}
                  disabled
                >
                  Mobile OTP
                </Button>
              </span>
            </Tooltip>
          </Box>

          <Typography variant="body2" sx={{ textAlign: 'center' }} color="text.secondary">
            Don't have an account?{' '}
            <Typography component={RouterLink} to="/register" variant="body2" color="primary.main" fontWeight={700} sx={{ textDecoration: 'none' }}>
              Create Account
            </Typography>
          </Typography>
        </Box>
      </Box>
    </Box>
  )
}
