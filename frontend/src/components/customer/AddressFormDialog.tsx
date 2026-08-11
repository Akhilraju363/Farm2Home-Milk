import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress, Alert } from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { customerService } from '../../services/customerService'
import type { CustomerAddress } from '../../types/customer.types'

const schema = yup.object({
  addressLine1: yup.string().trim().required('Address line 1 is required').max(255, 'Max 255 characters'),
  addressLine2: yup.string().max(255, 'Max 255 characters'),
  city: yup.string().trim().required('City is required').max(100, 'Max 100 characters'),
  state: yup.string().trim().required('State is required').max(100, 'Max 100 characters'),
  pincode: yup.string().trim().required('Pincode is required').matches(/^\d{6}$/, 'Enter a valid 6-digit pincode'),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  customerId: string
  address: CustomerAddress | null
  onClose: () => void
  onSaved: () => void
}

export function AddressFormDialog({ open, customerId, address, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(address)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { addressLine1: '', addressLine2: '', city: '', state: '', pincode: '' },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    reset({
      addressLine1: address?.addressLine1 ?? '',
      addressLine2: address?.addressLine2 ?? '',
      city: address?.city ?? '',
      state: address?.state ?? '',
      pincode: address?.pincode ?? '',
    })
  }, [open, address, reset])

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      const payload = {
        addressLine1: values.addressLine1.trim(),
        addressLine2: values.addressLine2?.trim() || undefined,
        city: values.city.trim(),
        state: values.state.trim(),
        pincode: values.pincode.trim(),
      }
      if (isEdit && address) {
        await customerService.updateAddress(customerId, address.id, payload)
      } else {
        await customerService.addAddressForCustomer(customerId, payload)
      }
      enqueueSnackbar(isEdit ? 'Address updated successfully' : 'Address added successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Address' : 'Add Address'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <TextField
            label="Address Line 1" required fullWidth size="small"
            error={!!errors.addressLine1} helperText={errors.addressLine1?.message}
            {...register('addressLine1')}
          />
          <TextField
            label="Address Line 2" fullWidth size="small"
            error={!!errors.addressLine2} helperText={errors.addressLine2?.message ?? 'Optional'}
            {...register('addressLine2')}
          />
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="City" required fullWidth size="small"
              error={!!errors.city} helperText={errors.city?.message}
              {...register('city')}
            />
            <TextField
              label="State" required fullWidth size="small"
              error={!!errors.state} helperText={errors.state?.message}
              {...register('state')}
            />
          </Box>
          <TextField
            label="Pincode" required fullWidth size="small"
            error={!!errors.pincode} helperText={errors.pincode?.message}
            {...register('pincode')}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Add Address'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
