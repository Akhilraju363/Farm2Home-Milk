import { Box, TextField, Button, Typography, InputAdornment, CircularProgress, Alert } from '@mui/material'
import { ContactMail, Agriculture, MarkEmailRead } from '@mui/icons-material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { Link as RouterLink } from 'react-router-dom'
import { authService } from '../../services/authService'

const MOBILE_PATTERN = /^[6-9]\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const schema = yup.object({
  identifier: yup.string()
    .required('Mobile number or email is required')
    .test(
      'valid-identifier',
      'Enter a valid 10-digit mobile number or email address',
      (value) => !!value && (MOBILE_PATTERN.test(value) || EMAIL_PATTERN.test(value)),
    ),
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

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: yupResolver(schema),
  })

  const onSubmit = async (data: FormData) => {
    setError('')
    try {
      await authService.sendOtp({ identifier: data.identifier, otpType: 'FORGOT_PASSWORD' })
      setSent(true)
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
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
          backgroundImage: 'linear-gradient(160deg, #4a7052 0%, #2f4f37 55%, #16281a 100%)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Agriculture />
          <Typography variant="h6" fontWeight={700}>Farm2Home</Typography>
        </Box>

        <Box>
          <Typography variant="h4" fontWeight={700} lineHeight={1.25}>
            Secure Your Freshness.
          </Typography>
          <Typography variant="body2" sx={{ mt: 1.5, opacity: 0.9, maxWidth: 380 }}>
            Reset your password to continue your farm-to-home journey.
          </Typography>
        </Box>
      </Box>

      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#f7faf8', p: 3 }}>
        <Box sx={{ width: '100%', maxWidth: 400 }}>
          {sent ? (
            <>
              <MarkEmailRead sx={{ fontSize: 40, color: 'primary.main', mb: 2 }} />
              <Typography variant="h5" fontWeight={700} mb={1}>Check your phone</Typography>
              <Typography variant="body2" color="text.secondary" mb={3}>
                If that email or mobile number is registered with us, we've sent a verification code to it.
              </Typography>
              <Button component={RouterLink} to="/login" variant="contained" fullWidth size="large" sx={{ py: 1.5 }}>
                Back to Login
              </Button>
            </>
          ) : (
            <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
              <Typography variant="h5" fontWeight={700} mb={0.5}>Forgot Password?</Typography>
              <Typography variant="body2" color="text.secondary" mb={3}>
                No worries! Enter your registered email or mobile number and we'll send you a verification code.
              </Typography>

              {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

              <TextField
                label="Email or Mobile Number"
                fullWidth
                margin="normal"
                sx={fieldSx}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <ContactMail fontSize="small" color="action" />
                    </InputAdornment>
                  ),
                }}
                error={!!errors.identifier}
                helperText={errors.identifier?.message}
                {...register('identifier')}
              />

              <Button
                type="submit"
                variant="contained"
                fullWidth
                size="large"
                disabled={isSubmitting}
                sx={{ mt: 2, mb: 2, py: 1.5 }}
              >
                {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Send Verification Code'}
              </Button>

              <Typography variant="body2" sx={{ textAlign: 'center' }}>
                <Typography component={RouterLink} to="/login" variant="body2" color="primary.main" fontWeight={700} sx={{ textDecoration: 'none' }}>
                  ← Back to Login
                </Typography>
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  )
}
