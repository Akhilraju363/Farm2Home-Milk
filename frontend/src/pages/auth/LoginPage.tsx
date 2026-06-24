import {
  Box, TextField, Button, Typography, Divider,
  InputAdornment, IconButton, CircularProgress, Alert,
} from '@mui/material'
import { Visibility, VisibilityOff, Phone } from '@mui/icons-material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import type { AppDispatch } from '../../store/store'
import { setCredentials } from '../../store/slices/authSlice'
import { authService } from '../../services/authService'

const schema = yup.object({
  mobile: yup.string().matches(/^[6-9]\d{9}$/, 'Enter valid 10-digit mobile').required('Mobile is required'),
  password: yup.string().min(6, 'Min 6 characters').required('Password is required'),
})

type FormData = yup.InferType<typeof schema>

export function LoginPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: yupResolver(schema),
  })

  const onSubmit = async (data: FormData) => {
    setError('')
    try {
      const res = await authService.login({ mobile: data.mobile, password: data.password })
      const { accessToken, refreshToken, user } = res.data
      localStorage.setItem('refreshToken', refreshToken)
      dispatch(setCredentials({ accessToken, user }))
      navigate('/dashboard', { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Invalid credentials. Please try again.')
    }
  }

  return (
    <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
      <Typography variant="h6" fontWeight={700} mb={0.5}>Welcome back</Typography>
      <Typography variant="body2" color="text.secondary" mb={3}>
        Sign in to your account to continue
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <TextField
        label="Mobile Number"
        fullWidth
        margin="normal"
        InputProps={{
          startAdornment: (
            <InputAdornment position="start"><Phone fontSize="small" /></InputAdornment>
          ),
        }}
        error={!!errors.mobile}
        helperText={errors.mobile?.message}
        {...register('mobile')}
      />

      <TextField
        label="Password"
        type={showPassword ? 'text' : 'password'}
        fullWidth
        margin="normal"
        InputProps={{
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

      <Button
        type="submit"
        variant="contained"
        fullWidth
        size="large"
        disabled={isSubmitting}
        sx={{ mt: 3, mb: 2, py: 1.5 }}
      >
        {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Sign In'}
      </Button>

      <Divider sx={{ my: 2 }}>
        <Typography variant="caption" color="text.secondary">OR</Typography>
      </Divider>

      <Button
        fullWidth
        variant="outlined"
        onClick={() => navigate('/register')}
      >
        Create Account
      </Button>
    </Box>
  )
}
