import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress, Alert,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { authService } from '../../services/authService'

// Customer-service has no direct "create customer" endpoint - a customer profile only ever comes
// into existence via auth-service's registration flow (see CustomerEventConsumer, which reacts to
// a Kafka event fired by /auth/register and creates the customer.customers row asynchronously).
// This dialog reuses that same real, existing endpoint rather than inventing a customer-service
// POST /customers that doesn't exist - the admin is registering a new account on the customer's
// behalf, exactly like RegisterPage's own self-service flow.
const schema = yup.object({
  firstName: yup.string().trim().required('First name is required').min(2, 'Min 2 characters').max(100, 'Max 100 characters'),
  lastName: yup.string().trim().required('Last name is required').min(2, 'Min 2 characters').max(100, 'Max 100 characters'),
  mobile: yup.string().trim().required('Mobile number is required').matches(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number'),
  email: yup.string().trim().email('Enter a valid email address'),
  password: yup.string().required('Password is required').min(8, 'Min 8 characters'),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  onClose: () => void
  onSaved: () => void
}

export function AddCustomerDialog({ open, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { firstName: '', lastName: '', mobile: '', email: '', password: '' },
  })

  useEffect(() => {
    if (open) { setServerError(''); reset() }
  }, [open, reset])

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      // Intentionally not dispatched to Redux / tokenStorage - this response's tokens belong to
      // the newly-created customer, not the admin submitting this form, and must never replace
      // the admin's own session.
      await authService.register({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        mobile: values.mobile.trim(),
        email: values.email?.trim() || undefined,
        password: values.password,
      })
      enqueueSnackbar('Customer account created successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Add Customer</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <Alert severity="info" sx={{ fontSize: 13 }}>
            This creates a real account the customer can log in with - a customer profile only
            exists once one is registered.
          </Alert>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="First Name" required fullWidth size="small"
              error={!!errors.firstName} helperText={errors.firstName?.message}
              {...register('firstName')}
            />
            <TextField
              label="Last Name" required fullWidth size="small"
              error={!!errors.lastName} helperText={errors.lastName?.message}
              {...register('lastName')}
            />
          </Box>
          <TextField
            label="Mobile Number" required fullWidth size="small"
            error={!!errors.mobile} helperText={errors.mobile?.message}
            {...register('mobile')}
          />
          <TextField
            label="Email" fullWidth size="small"
            error={!!errors.email} helperText={errors.email?.message ?? 'Optional'}
            {...register('email')}
          />
          <TextField
            label="Password" type="password" required fullWidth size="small"
            error={!!errors.password} helperText={errors.password?.message ?? 'The customer can change this later'}
            {...register('password')}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : 'Create Customer'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
