import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, MenuItem, Box, CircularProgress, Alert,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { customerService } from '../../services/customerService'
import type { Customer } from '../../types/customer.types'

const schema = yup.object({
  firstName: yup.string().trim().required('First name is required').min(2, 'Min 2 characters').max(100, 'Max 100 characters'),
  lastName: yup.string().trim().required('Last name is required').min(2, 'Min 2 characters').max(100, 'Max 100 characters'),
  email: yup.string().trim().email('Enter a valid email address'),
  status: yup.string().oneOf(['ACTIVE', 'INACTIVE', 'SUSPENDED']).required(),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  customer: Customer | null
  onClose: () => void
  onSaved: () => void
}

export function EditCustomerDialog({ open, customer, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { control, register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', status: 'ACTIVE' },
  })

  useEffect(() => {
    if (!open || !customer) return
    setServerError('')
    reset({
      firstName: customer.firstName,
      lastName: customer.lastName,
      email: customer.email ?? '',
      status: customer.status,
    })
  }, [open, customer, reset])

  const onSubmit = async (values: FormValues) => {
    if (!customer) return
    setServerError('')
    setSubmitting(true)
    try {
      await customerService.update(customer.id, {
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email?.trim() || undefined,
        status: values.status as Customer['status'],
      })
      enqueueSnackbar('Customer updated successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Edit Customer</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <TextField
            label="Mobile Number" value={customer?.mobile ?? ''} fullWidth size="small" disabled
            helperText="Mobile number cannot be changed"
          />
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
            label="Email" fullWidth size="small"
            error={!!errors.email} helperText={errors.email?.message ?? 'Optional'}
            {...register('email')}
          />
          <Controller
            name="status"
            control={control}
            render={({ field }) => (
              <TextField {...field} select label="Status" fullWidth size="small">
                <MenuItem value="ACTIVE">Active</MenuItem>
                <MenuItem value="INACTIVE">Inactive</MenuItem>
                <MenuItem value="SUSPENDED">Suspended</MenuItem>
              </TextField>
            )}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : 'Save Changes'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
