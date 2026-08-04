import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Box, Paper, TextField, Button, Typography, IconButton,
  InputAdornment, CircularProgress, Alert, LinearProgress, MenuItem,
  ToggleButton, ToggleButtonGroup,
} from '@mui/material'
import {
  Agriculture, Visibility, VisibilityOff,
  VerifiedUser, LocalShipping, Lock, ArrowBack, ArrowForward, Spa, Schedule,
  Pets, WaterDrop, LocalFlorist, WbTwilight, WbSunny, LightMode as SunIcon,
} from '@mui/icons-material'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import type { AppDispatch } from '../../store/store'
import { setCredentials } from '../../store/slices/authSlice'
import { authService } from '../../services/authService'
import { customerService } from '../../services/customerService'
import { tokenStorage } from '../../services/tokenStorage'

const TOTAL_STEPS = 4
const RESEND_COOLDOWN_SECONDS = 60

const STATES = ['Maharashtra', 'Karnataka', 'Delhi', 'Tamil Nadu', 'Telangana', 'Gujarat', 'West Bengal', 'Uttar Pradesh']
const CITIES = ['Mumbai', 'Pune', 'Bengaluru', 'New Delhi', 'Chennai', 'Hyderabad', 'Ahmedabad', 'Kolkata', 'Lucknow']

const MILK_TYPES = [
  { value: 'COW', label: 'Cow', icon: <Pets fontSize="small" /> },
  { value: 'BUFFALO', label: 'Buffalo', icon: <WaterDrop fontSize="small" /> },
  { value: 'ORGANIC', label: 'Organic', icon: <LocalFlorist fontSize="small" /> },
] as const

const QUANTITIES = ['0.5 L', '1.0 L', '2.0 L']

const DELIVERY_SLOTS = [
  { value: 'EARLY_MORNING', label: 'Early Morning', range: '06:00 AM - 07:00 AM', icon: <WbTwilight fontSize="small" /> },
  { value: 'MORNING_RUSH', label: 'Morning Rush', range: '07:00 AM - 08:00 AM', icon: <WbSunny fontSize="small" /> },
  { value: 'DAY_START', label: 'Day Start', range: '08:00 AM - 09:00 AM', icon: <SunIcon fontSize="small" /> },
] as const

interface PanelBadge {
  icon?: JSX.Element
  title: string
  caption: string
}

interface StepPanel {
  heading: string
  subtext: string
  badgeStyle: 'card' | 'inline'
  badges: PanelBadge[]
}

const stepPanels: Record<number, StepPanel> = {
  1: {
    heading: 'Freshness delivered, from our fields to your fridge.',
    subtext: 'Join 50,000+ families enjoying pure, unadulterated farm produce every single morning.',
    badgeStyle: 'card',
    badges: [
      { icon: <VerifiedUser color="success" fontSize="small" />, title: 'Purity Guaranteed', caption: 'Zero preservatives added.' },
      { icon: <LocalShipping color="success" fontSize="small" />, title: 'Before 7 AM', caption: 'Timely morning delivery.' },
    ],
  },
  2: {
    heading: 'Fresh from our fields to your doorstep.',
    subtext: 'We ensure the highest quality milk through transparent sourcing and direct farm relationships.',
    badgeStyle: 'inline',
    badges: [
      { icon: <Spa fontSize="small" />, title: '100%', caption: 'ORGANIC' },
      { icon: <Schedule fontSize="small" />, title: '24h', caption: 'DELIVERY' },
    ],
  },
  3: {
    heading: 'Tailored freshness, every single morning.',
    subtext: "Configure your subscription to match your family's needs perfectly. Change anytime with a single tap.",
    badgeStyle: 'inline',
    badges: [
      { icon: <LocalFlorist fontSize="small" />, title: '100%', caption: 'ORGANIC' },
      { icon: <Schedule fontSize="small" />, title: '24h', caption: 'DELIVERY' },
    ],
  },
  4: {
    heading: 'Digital Freshness Delivered',
    subtext: 'We ensure every drop of freshness is verified and protected until it reaches your doorstep.',
    badgeStyle: 'inline',
    badges: [
      { icon: <VerifiedUser color="success" fontSize="small" />, title: 'Secure', caption: 'END-TO-END ENCRYPTED' },
    ],
  },
}

const personalDetailsSchema = yup.object({
  fullName: yup
    .string()
    .required('Full name is required')
    .matches(/^\S+\s+\S+/, 'Enter your first and last name'),
  mobile: yup.string()
    .matches(/^\d{10}$/, 'Mobile number must be exactly 10 digits')
    .matches(/^[6-9]/, 'Enter a valid Indian mobile number')
    .required('Mobile is required'),
  email: yup.string().email('Enter a valid email address').notRequired(),
  password: yup
    .string()
    .required('Password is required')
    .min(8, 'Min 8 characters')
    .matches(/[a-z]/, 'Add a lowercase letter')
    .matches(/[A-Z]/, 'Add an uppercase letter')
    .matches(/\d/, 'Add a digit')
    .matches(/[!@#$%^&*(),.?":{}|<>]/, 'Add a special character'),
})

type PersonalDetails = yup.InferType<typeof personalDetailsSchema>

const addressDetailsSchema = yup.object({
  houseNo: yup.string().required('House/Flat No. is required'),
  street: yup.string().required('Street/Landmark is required'),
  area: yup.string().required('Area/Locality is required'),
  city: yup.string().required('Select a city'),
  pincode: yup.string().matches(/^\d{6}$/, 'Enter a valid 6-digit pincode').required('Pincode is required'),
  state: yup.string().required('Select a state'),
})

type AddressDetails = yup.InferType<typeof addressDetailsSchema>

interface MilkPreferences {
  milkType: (typeof MILK_TYPES)[number]['value']
  quantity: string
  deliverySlot: (typeof DELIVERY_SLOTS)[number]['value']
}

function passwordStrength(password: string) {
  const rules = [/.{8,}/, /[a-z]/, /[A-Z]/, /\d/, /[!@#$%^&*(),.?":{}|<>]/]
  const score = rules.filter((r) => r.test(password)).length
  const labels = ['Very Weak', 'Weak', 'Fair', 'Good', 'Strong']
  const colors = ['error', 'error', 'warning', 'info', 'success'] as const
  return { score, label: labels[score], color: colors[score] }
}

function maskMobile(mobile: string) {
  return mobile.length === 10 ? `+91 ${mobile.slice(0, 2)}${'*'.repeat(6)}${mobile.slice(-2)}` : mobile
}

function PersonalDetailsStep({
  defaultValues, error, submitting, onNext,
}: {
  defaultValues?: Partial<PersonalDetails>
  error: string
  submitting: boolean
  onNext: (data: PersonalDetails) => void
}) {
  const [showPassword, setShowPassword] = useState(false)
  const {
    register, handleSubmit, watch,
    formState: { errors },
  } = useForm<PersonalDetails>({ resolver: yupResolver(personalDetailsSchema), defaultValues })

  const password = watch('password') ?? ''
  const strength = useMemo(() => passwordStrength(password), [password])
  const mobileField = register('mobile')

  return (
    <Box component="form" onSubmit={handleSubmit(onNext)} noValidate>
      <TextField
        label="Full Name"
        fullWidth
        margin="normal"
        error={!!errors.fullName}
        helperText={errors.fullName?.message}
        {...register('fullName')}
      />

      <Box sx={{ display: 'flex', gap: 2 }}>
        <TextField
          label="Mobile Number"
          fullWidth
          margin="normal"
          InputProps={{
            startAdornment: <InputAdornment position="start">+91</InputAdornment>,
          }}
          inputProps={{ inputMode: 'numeric', maxLength: 10 }}
          error={!!errors.mobile}
          helperText={errors.mobile?.message}
          {...mobileField}
          onChange={(e) => {
            e.target.value = e.target.value.replace(/\D/g, '').slice(0, 10)
            mobileField.onChange(e)
          }}
        />
        <TextField
          label="Email Address"
          fullWidth
          margin="normal"
          error={!!errors.email}
          helperText={errors.email?.message}
          {...register('email')}
        />
      </Box>

      <TextField
        label="Password"
        type={showPassword ? 'text' : 'password'}
        fullWidth
        margin="normal"
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton onClick={() => setShowPassword((s) => !s)} edge="end" size="small">
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        }}
        error={!!errors.password}
        helperText={errors.password?.message}
        {...register('password')}
      />

      {password && (
        <Box sx={{ mt: 0.5, mb: 1 }}>
          <LinearProgress
            variant="determinate"
            value={(strength.score / 5) * 100}
            color={strength.color}
            sx={{ height: 6, borderRadius: 3 }}
          />
          <Typography variant="caption" color="text.secondary">
            Security Level: {strength.label}
          </Typography>
        </Box>
      )}

      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
        <Button
          type="submit"
          variant="contained"
          size="large"
          disabled={submitting}
          endIcon={submitting ? undefined : <ArrowForward />}
          sx={{ py: 1.5, px: 4 }}
        >
          {submitting ? <CircularProgress size={22} color="inherit" /> : 'Next Step'}
        </Button>
      </Box>

      <Typography variant="body2" sx={{ mt: 2, textAlign: 'center' }} color="text.secondary">
        Already have an account?{' '}
        <Typography component={RouterLink} to="/login" variant="body2" color="primary.main" fontWeight={700} sx={{ textDecoration: 'none' }}>
          Log in
        </Typography>
      </Typography>
    </Box>
  )
}

function AddressDetailsStep({
  warning, onBack, onNext,
}: {
  warning: string
  onBack: () => void
  onNext: (data: AddressDetails) => void
}) {
  const {
    register, handleSubmit,
    formState: { errors },
  } = useForm<AddressDetails>({ resolver: yupResolver(addressDetailsSchema) })

  return (
    <Box component="form" onSubmit={handleSubmit(onNext)} noValidate>
      <Typography variant="h6" fontWeight={700} mb={0.5}>
        Where should we deliver?
      </Typography>
      <Typography variant="body2" color="text.secondary" mb={2}>
        Provide your address details to check delivery availability in your area.
      </Typography>

      {warning && <Alert severity="warning" sx={{ mb: 2 }}>{warning}</Alert>}

      <Box sx={{ display: 'flex', gap: 2 }}>
        <TextField
          label="House / Flat No."
          fullWidth
          margin="normal"
          error={!!errors.houseNo}
          helperText={errors.houseNo?.message}
          {...register('houseNo')}
        />
        <TextField
          label="Street / Landmark"
          fullWidth
          margin="normal"
          error={!!errors.street}
          helperText={errors.street?.message}
          {...register('street')}
        />
      </Box>

      <TextField
        label="Area / Locality"
        fullWidth
        margin="normal"
        error={!!errors.area}
        helperText={errors.area?.message}
        {...register('area')}
      />

      <Box sx={{ display: 'flex', gap: 2 }}>
        <TextField
          select
          label="City"
          fullWidth
          margin="normal"
          defaultValue=""
          error={!!errors.city}
          helperText={errors.city?.message}
          {...register('city')}
        >
          {CITIES.map((city) => (
            <MenuItem key={city} value={city}>{city}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="Pincode"
          fullWidth
          margin="normal"
          error={!!errors.pincode}
          helperText={errors.pincode?.message}
          {...register('pincode')}
        />
      </Box>

      <TextField
        select
        label="State"
        fullWidth
        margin="normal"
        defaultValue=""
        error={!!errors.state}
        helperText={errors.state?.message}
        {...register('state')}
      >
        {STATES.map((state) => (
          <MenuItem key={state} value={state}>{state}</MenuItem>
        ))}
      </TextField>

      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 2 }}>
        <Button onClick={onBack} startIcon={<ArrowBack />} color="primary">
          Back
        </Button>
        <Button type="submit" variant="contained" size="large" endIcon={<ArrowForward />} sx={{ py: 1.5, px: 4 }}>
          Next Step
        </Button>
      </Box>

      <Typography variant="body2" sx={{ mt: 2, textAlign: 'center' }} color="text.secondary">
        Need help with your address? <Typography component="span" variant="body2" color="primary.main" fontWeight={700}>Contact Support</Typography>
      </Typography>
    </Box>
  )
}

function MilkPreferencesStep({ onBack, onNext }: { onBack: () => void; onNext: (data: MilkPreferences) => void }) {
  const [milkType, setMilkType] = useState<MilkPreferences['milkType']>('COW')
  const [quantity, setQuantity] = useState(QUANTITIES[1])
  const [deliverySlot, setDeliverySlot] = useState<MilkPreferences['deliverySlot']>('EARLY_MORNING')

  return (
    <Box>
      <Typography variant="h6" fontWeight={700} mb={0.5}>
        Milk Preferences
      </Typography>
      <Typography variant="body2" color="text.secondary" mb={2}>
        Tell us how you like your morning delivery.
      </Typography>

      <Typography variant="subtitle2" fontWeight={700} color="primary.main" mb={1}>
        Choose Milk Type
      </Typography>
      <Box sx={{ display: 'flex', gap: 1.5, mb: 3 }}>
        {MILK_TYPES.map((type) => (
          <Paper
            key={type.value}
            variant="outlined"
            onClick={() => setMilkType(type.value)}
            sx={{
              flex: 1, p: 1.5, textAlign: 'center', cursor: 'pointer',
              borderColor: milkType === type.value ? 'primary.main' : undefined,
              bgcolor: milkType === type.value ? 'success.light' : undefined,
              borderWidth: milkType === type.value ? 2 : 1,
            }}
          >
            {type.icon}
            <Typography variant="body2" fontWeight={700}>{type.label}</Typography>
          </Paper>
        ))}
      </Box>

      <Typography variant="subtitle2" fontWeight={700} color="primary.main" mb={1}>
        Daily Quantity
      </Typography>
      <ToggleButtonGroup
        exclusive
        value={quantity}
        onChange={(_e, val) => val && setQuantity(val)}
        fullWidth
        sx={{ mb: 3 }}
      >
        {QUANTITIES.map((q) => (
          <ToggleButton key={q} value={q}>{q}</ToggleButton>
        ))}
      </ToggleButtonGroup>

      <Typography variant="subtitle2" fontWeight={700} color="primary.main" mb={1}>
        Preferred Delivery Slot
      </Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 3 }}>
        {DELIVERY_SLOTS.map((slot) => (
          <Paper
            key={slot.value}
            variant="outlined"
            onClick={() => setDeliverySlot(slot.value)}
            sx={{
              p: 1.5, display: 'flex', alignItems: 'center', gap: 1.5, cursor: 'pointer',
              borderColor: deliverySlot === slot.value ? 'primary.main' : undefined,
              borderWidth: deliverySlot === slot.value ? 2 : 1,
            }}
          >
            {slot.icon}
            <Box>
              <Typography variant="body2" fontWeight={700}>{slot.label}</Typography>
              <Typography variant="caption" color="text.secondary">{slot.range}</Typography>
            </Box>
          </Paper>
        ))}
      </Box>

      <Alert severity="info" sx={{ mb: 2 }}>
        You can modify your daily quantity or pause delivery up to 10:00 PM the night before delivery.
        No long-term commitment required.
      </Alert>

      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Button onClick={onBack} startIcon={<ArrowBack />} color="primary">
          Back
        </Button>
        <Button
          variant="contained"
          size="large"
          endIcon={<ArrowForward />}
          sx={{ py: 1.5, px: 4 }}
          onClick={() => onNext({ milkType, quantity, deliverySlot })}
        >
          Next Step
        </Button>
      </Box>
    </Box>
  )
}

function VerificationStep({
  mobile, error, submitting, onVerify, onResend, onBack,
}: {
  mobile: string
  error: string
  submitting: boolean
  onVerify: (otp: string) => void
  onResend: () => void
  onBack: () => void
}) {
  const [digits, setDigits] = useState(['', '', '', '', '', ''])
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS)
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setInterval(() => setCooldown((c) => c - 1), 1000)
    return () => clearInterval(timer)
  }, [cooldown])

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

  const handleResend = () => {
    setCooldown(RESEND_COOLDOWN_SECONDS)
    onResend()
  }

  const otp = digits.join('')
  const minutes = String(Math.floor(cooldown / 60)).padStart(2, '0')
  const seconds = String(cooldown % 60).padStart(2, '0')

  return (
    <Box>
      <Typography variant="overline" color="primary.main" fontWeight={700}>
        Step 4 of {TOTAL_STEPS}
      </Typography>
      <Typography variant="h6" fontWeight={700} mb={0.5}>
        Security Verification
      </Typography>
      <Typography variant="body2" color="text.secondary" mb={3}>
        We've sent a 6-digit verification code to <b>{maskMobile(mobile)}</b>. Please enter it below to complete your profile.
      </Typography>

      <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
        {digits.map((digit, i) => (
          <TextField
            key={i}
            inputRef={(el) => (inputRefs.current[i] = el)}
            value={digit}
            onChange={(e) => handleDigitChange(i, e.target.value)}
            onKeyDown={(e) => handleKeyDown(i, e)}
            inputProps={{ maxLength: 1, style: { textAlign: 'center', fontSize: '1.25rem' } }}
            sx={{ width: 48 }}
          />
        ))}
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
        <Button onClick={onBack} startIcon={<ArrowBack />} color="primary" disabled={submitting}>
          Back
        </Button>
        <Button
          variant="contained"
          size="large"
          disabled={otp.length !== 6 || submitting}
          endIcon={submitting ? undefined : <ArrowForward />}
          sx={{ py: 1.5, px: 4 }}
          onClick={() => onVerify(otp)}
        >
          {submitting ? <CircularProgress size={22} color="inherit" /> : 'Verify & Finish'}
        </Button>
      </Box>

      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Button size="small" disabled={cooldown > 0} onClick={handleResend} sx={{ textTransform: 'none' }}>
          Resend Code
        </Button>
        {cooldown > 0 && (
          <Typography variant="caption" color="text.secondary">{minutes}:{seconds}</Typography>
        )}
      </Box>

      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }} color="text.secondary">
        Having trouble? <Typography component="span" variant="body2" color="primary.main" fontWeight={700}>Contact Farm2Home Support</Typography>
      </Typography>
    </Box>
  )
}

export function RegisterPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const [step, setStep] = useState(1)
  const [error, setError] = useState('')
  const [addressWarning, setAddressWarning] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [personalDetails, setPersonalDetails] = useState<PersonalDetails>()

  const panel = stepPanels[step] ?? stepPanels[1]
  const stepLabels = ['Personal Details', 'Address Details', 'Milk Preferences', 'Verification']
  const stepLabel = stepLabels[step - 1]

  const handlePersonalDetailsNext = async (data: PersonalDetails) => {
    setError('')
    setSubmitting(true)
    const [firstName, ...rest] = data.fullName.trim().split(/\s+/)
    try {
      const res = await authService.register({
        firstName,
        lastName: rest.join(' '),
        mobile: data.mobile,
        email: data.email || undefined,
        password: data.password,
      })
      const { accessToken, refreshToken, user } = res.data.data
      tokenStorage.setTokens(accessToken, refreshToken, true)
      dispatch(setCredentials({ accessToken, user }))
      setPersonalDetails(data)
      setStep(2)
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Registration failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleAddressDetailsNext = async (address: AddressDetails) => {
    setAddressWarning('')
    try {
      await customerService.addAddress({
        addressLine1: `${address.houseNo}, ${address.street}`,
        addressLine2: address.area,
        city: address.city,
        state: address.state,
        pincode: address.pincode,
      })
    } catch {
      setAddressWarning("We couldn't save your address just now — you can add it later from your profile.")
    }
    setStep(3)
  }

  const handleMilkPreferencesNext = (_preferences: MilkPreferences) => {
    setStep(4)
  }

  const handleVerify = async (otp: string) => {
    if (!personalDetails) return
    setError('')
    setSubmitting(true)
    try {
      await authService.verifyOtp(personalDetails.mobile, otp, 'REGISTRATION')
      navigate('/dashboard', { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Invalid or expired code. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleResend = () => {
    if (!personalDetails) return
    authService.sendOtp({ identifier: personalDetails.mobile, otpType: 'REGISTRATION' }).catch(() => {})
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#f2f7f3', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 3, py: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Agriculture sx={{ color: 'primary.main' }} />
          <Typography variant="h6" fontWeight={700} color="primary.main">
            Farm2Home
          </Typography>
        </Box>
      </Box>

      <Box sx={{ flex: 1, display: 'flex', gap: 4, px: 3, pb: 3, flexDirection: { xs: 'column', md: 'row' } }}>
        <Box sx={{ flex: 1, maxWidth: { md: 460 } }}>
          <Box
            sx={{
              position: 'relative',
              borderRadius: 3,
              overflow: 'hidden',
              minHeight: 340,
              display: 'flex',
              alignItems: 'flex-end',
              p: 3,
              backgroundImage: 'linear-gradient(160deg, #6d8f6a 0%, #3f6b45 55%, #1f3d24 100%)',
              color: '#fff',
            }}
          >
            <Box sx={{ width: '100%' }}>
              <Typography variant="h5" fontWeight={700} lineHeight={1.25}>
                {panel.heading}
              </Typography>
              <Typography variant="body2" sx={{ mt: 1, opacity: 0.9 }}>
                {panel.subtext}
              </Typography>

              {panel.badgeStyle === 'inline' && (
                <Box sx={{ display: 'flex', gap: 3, mt: 2 }}>
                  {panel.badges.map((badge) => (
                    <Box key={badge.title} sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      {badge.icon}
                      <Box>
                        <Typography variant="subtitle1" fontWeight={700} lineHeight={1}>{badge.title}</Typography>
                        <Typography variant="caption" sx={{ opacity: 0.85, letterSpacing: 0.5 }}>{badge.caption}</Typography>
                      </Box>
                    </Box>
                  ))}
                </Box>
              )}
            </Box>
          </Box>

          {panel.badgeStyle === 'card' && (
            <Box sx={{ display: 'flex', gap: 2, mt: 2 }}>
              {panel.badges.map((badge) => (
                <Paper key={badge.title} variant="outlined" sx={{ flex: 1, p: 1.5, bgcolor: '#ffffff' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {badge.icon}
                    <Typography variant="subtitle2" fontWeight={700}>
                      {badge.title}
                    </Typography>
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    {badge.caption}
                  </Typography>
                </Paper>
              ))}
            </Box>
          )}
        </Box>

        <Paper sx={{ flex: 1, p: 4, borderRadius: 3, bgcolor: '#ffffff', maxWidth: { md: 480 } }}>
          {step !== 4 && (
            <>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 0.5 }}>
                <Typography variant="subtitle2" fontWeight={700} color="primary.main">
                  Step {step} of {TOTAL_STEPS}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {stepLabel}
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={(step / TOTAL_STEPS) * 100}
                sx={{ height: 4, borderRadius: 2, mb: 3 }}
              />
            </>
          )}

          {step === 1 && (
            <PersonalDetailsStep
              defaultValues={personalDetails}
              error={error}
              submitting={submitting}
              onNext={handlePersonalDetailsNext}
            />
          )}
          {step === 2 && (
            <AddressDetailsStep warning={addressWarning} onBack={() => setStep(1)} onNext={handleAddressDetailsNext} />
          )}
          {step === 3 && (
            <MilkPreferencesStep onBack={() => setStep(2)} onNext={handleMilkPreferencesNext} />
          )}
          {step === 4 && personalDetails && (
            <VerificationStep
              mobile={personalDetails.mobile}
              error={error}
              submitting={submitting}
              onVerify={handleVerify}
              onResend={handleResend}
              onBack={() => setStep(3)}
            />
          )}
        </Paper>
      </Box>

      <Box sx={{ textAlign: 'center', py: 2 }}>
        <Typography variant="caption" color="text.secondary">
          <Lock sx={{ fontSize: 12, verticalAlign: 'middle', mr: 0.5 }} />
          &copy; {new Date().getFullYear()} Farm2Home Premium. All rights reserved. Secure Registration Portal.
        </Typography>
      </Box>
    </Box>
  )
}
