import {
  Dialog, DialogContent, Box, Typography, TextField, Button, Alert, CircularProgress,
  IconButton, InputAdornment,
} from '@mui/material'
import { Close, ArrowBack, ArrowForward, Sms } from '@mui/icons-material'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import type { AppDispatch } from '../../store/store'
import { setCredentials } from '../../store/slices/authSlice'
import { authService } from '../../services/authService'
import { tokenStorage } from '../../services/tokenStorage'
import { getLandingRoute } from '../../utils/roleLanding'

const RESEND_COOLDOWN_SECONDS = 30 // mirrors auth-service's OTP_RESEND_COOLDOWN_SECONDS default

interface MobileOtpDialogProps {
  open: boolean
  onClose: () => void
}

/** Passwordless Mobile OTP sign-in - reuses the exact same send-otp/verify-otp endpoints
 *  RegisterPage's own verification step uses (otpType='LOGIN' here vs 'REGISTRATION' there), and
 *  the same tokenStorage/setCredentials/getLandingRoute mechanism LoginPage's password flow uses,
 *  so an OTP-authenticated session is indistinguishable from a password-authenticated one
 *  everywhere else in the app. A mobile number with no existing account is handed off to the
 *  existing registration wizard (mobile pre-filled) rather than this dialog ever creating an
 *  account itself - see RegisterPage's location.state handling. */
export function MobileOtpDialog({ open, onClose }: MobileOtpDialogProps) {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()

  const [step, setStep] = useState<'mobile' | 'otp'>('mobile')
  const [mobile, setMobile] = useState('')
  const [mobileError, setMobileError] = useState('')
  const [digits, setDigits] = useState(['', '', '', '', '', ''])
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setInterval(() => setCooldown((c) => c - 1), 1000)
    return () => clearInterval(timer)
  }, [cooldown])

  // Reset to a clean slate every time the dialog is (re)opened, not just unmounted - the same
  // dialog instance is reused across opens (see LoginPage), so stale digits/errors from a
  // previous attempt must never carry over silently.
  useEffect(() => {
    if (open) {
      setStep('mobile')
      setMobile('')
      setMobileError('')
      setDigits(['', '', '', '', '', ''])
      setError('')
      setSending(false)
      setVerifying(false)
      setCooldown(0)
    }
  }, [open])

  const describeError = (err: any, fallback: string) => {
    if (!err?.response) return 'Network error. Please check your connection and try again.'
    return err.response?.data?.message ?? fallback
  }

  const sendOtp = async () => {
    if (!/^[6-9]\d{9}$/.test(mobile)) {
      setMobileError('Enter a valid 10-digit Indian mobile number')
      return
    }
    setMobileError('')
    setError('')
    setSending(true)
    try {
      await authService.sendOtp({ identifier: mobile, otpType: 'LOGIN' })
      setStep('otp')
      setCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (err: any) {
      // Covers the resend-cooldown/resend-limit responses (see OtpService) as well as a genuine
      // network failure - both are real, reachable states, unlike a distinguishable "SMS
      // provider failed" state: SmsService never reports delivery failure back through this API
      // (a transient SMS outage must never block OTP generation - see OtpService.generateAndSend),
      // so send-otp always either succeeds or is rate-limited from this dialog's point of view.
      setError(describeError(err, 'Could not send OTP. Please try again.'))
    } finally {
      setSending(false)
    }
  }

  const handleResend = () => {
    if (cooldown > 0 || sending) return
    sendOtp()
  }

  const handleDigitChange = (index: number, value: string) => {
    if (!/^\d?$/.test(value)) return
    const next = [...digits]
    next[index] = value
    setDigits(next)
    if (value && index < 5) inputRefs.current[index + 1]?.focus()
  }

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus()
    }
  }

  const verifyOtp = async () => {
    const otp = digits.join('')
    if (otp.length !== 6) return
    setError('')
    setVerifying(true)
    try {
      const res = await authService.verifyOtp(mobile, otp, 'LOGIN')
      const { registrationRequired, auth } = res.data.data
      if (auth) {
        tokenStorage.setTokens(auth.accessToken, auth.refreshToken, true)
        dispatch(setCredentials({ accessToken: auth.accessToken, user: auth.user }))
        onClose()
        navigate(getLandingRoute(auth.user.roles), { replace: true })
        return
      }
      if (registrationRequired) {
        onClose()
        // Verified ownership of this number - RegisterPage pre-fills it and still runs its own
        // REGISTRATION OTP step (a deliberate, accepted redundancy - see
        // AUTH_SOCIAL_OTP_PROGRESS.md - rather than a second, parallel account-creation path).
        navigate('/register', { state: { mobile } })
        return
      }
      // Neither auth nor registrationRequired - shouldn't happen given the backend contract, but
      // never leave the dialog silently stuck if it does.
      setError('Something went wrong. Please try again.')
    } catch (err: any) {
      setError(describeError(err, 'Invalid or expired code. Please try again.'))
    } finally {
      setVerifying(false)
    }
  }

  const otp = digits.join('')
  const minutes = String(Math.floor(cooldown / 60)).padStart(2, '0')
  const seconds = String(cooldown % 60).padStart(2, '0')

  return (
    <Dialog open={open} onClose={sending || verifying ? undefined : onClose} maxWidth="xs" fullWidth
      aria-labelledby="mobile-otp-dialog-title">
      <DialogContent sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Sms color="primary" />
            <Typography id="mobile-otp-dialog-title" variant="h6" fontWeight={700}>
              {step === 'mobile' ? 'Sign in with Mobile OTP' : 'Enter Verification Code'}
            </Typography>
          </Box>
          <IconButton aria-label="Close" onClick={onClose} size="small" disabled={sending || verifying}>
            <Close fontSize="small" />
          </IconButton>
        </Box>

        {step === 'mobile' && (
          <Box>
            <Typography variant="body2" color="text.secondary" mb={2.5}>
              We'll send a 6-digit code to your registered mobile number.
            </Typography>
            <TextField
              autoFocus
              fullWidth
              label="Mobile Number"
              placeholder="9876543210"
              value={mobile}
              onChange={(e) => setMobile(e.target.value.replace(/\D/g, '').slice(0, 10))}
              error={!!mobileError}
              helperText={mobileError}
              disabled={sending}
              InputProps={{ startAdornment: <InputAdornment position="start">+91</InputAdornment> }}
              onKeyDown={(e) => e.key === 'Enter' && sendOtp()}
              inputProps={{ inputMode: 'numeric', 'aria-label': 'Mobile number' }}
            />
            {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            <Button
              fullWidth
              variant="contained"
              size="large"
              sx={{ mt: 2.5, py: 1.3 }}
              disabled={sending || mobile.length !== 10}
              onClick={sendOtp}
              endIcon={sending ? undefined : <ArrowForward />}
            >
              {sending ? <CircularProgress size={22} color="inherit" /> : 'Send OTP'}
            </Button>
          </Box>
        )}

        {step === 'otp' && (
          <Box>
            <Typography variant="body2" color="text.secondary" mb={2.5}>
              Enter the 6-digit code sent to <b>+91 {mobile}</b>.
            </Typography>

            <Box sx={{ display: 'flex', gap: 1, mb: 2, justifyContent: 'center' }}>
              {digits.map((digit, i) => (
                <TextField
                  key={i}
                  inputRef={(el) => (inputRefs.current[i] = el)}
                  value={digit}
                  onChange={(e) => handleDigitChange(i, e.target.value)}
                  onKeyDown={(e) => handleKeyDown(i, e)}
                  disabled={verifying}
                  inputProps={{
                    maxLength: 1, inputMode: 'numeric',
                    style: { textAlign: 'center', fontSize: '1.25rem' },
                    'aria-label': `Digit ${i + 1} of 6`,
                  }}
                  sx={{ width: 46 }}
                />
              ))}
            </Box>

            {error && <Alert severity="error" sx={{ mb: 2 }} role="alert">{error}</Alert>}

            <Button
              fullWidth
              variant="contained"
              size="large"
              sx={{ py: 1.3 }}
              disabled={otp.length !== 6 || verifying}
              onClick={verifyOtp}
            >
              {verifying ? <CircularProgress size={22} color="inherit" /> : 'Verify OTP'}
            </Button>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1.5 }}>
              <Button
                size="small"
                startIcon={<ArrowBack fontSize="small" />}
                onClick={() => { setStep('mobile'); setDigits(['', '', '', '', '', '']); setError('') }}
                disabled={verifying}
                sx={{ textTransform: 'none' }}
              >
                Change number
              </Button>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {cooldown > 0 ? (
                  <Typography variant="caption" color="text.secondary">
                    Resend in {minutes}:{seconds}
                  </Typography>
                ) : (
                  <Button size="small" onClick={handleResend} disabled={sending} sx={{ textTransform: 'none' }}>
                    {sending ? 'Sending…' : 'Resend OTP'}
                  </Button>
                )}
              </Box>
            </Box>
          </Box>
        )}
      </DialogContent>
    </Dialog>
  )
}
