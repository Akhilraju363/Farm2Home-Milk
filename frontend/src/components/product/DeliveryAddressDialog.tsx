import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress,
  Alert, RadioGroup, FormControlLabel, Radio, Typography, Divider, Chip, IconButton, Tooltip,
} from '@mui/material'
import { Close, Add, MyLocation, CheckCircle, LocationOff, Edit } from '@mui/icons-material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { customerService } from '../../services/customerService'
import { useGeolocationCapture } from '../../hooks/useGeolocationCapture'
import { EditAddressDialog } from './EditAddressDialog'
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
  onClose: () => void
  /** Fires after the default address changes or a new one is added, so the Shop page's
   *  delivery-availability check can refetch against the (possibly new) default address. */
  onChanged: () => void
}

/** Self-service delivery-address management, reached from the Shop page's "Change Address"
 *  action. Uses the same /me/addresses (add) and /{customerId}/addresses (list/set-default)
 *  endpoints as everywhere else - no separate API. */
export function DeliveryAddressDialog({ open, customerId, onClose, onChanged }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [editingAddress, setEditingAddress] = useState<CustomerAddress | null>(null)
  const { coords, locating, error: locationError, capture, reset: resetLocation } = useGeolocationCapture()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['customers', customerId, 'addresses'],
    queryFn: () => customerService.getAddresses(customerId),
    enabled: open,
  })
  const addresses = data?.data.data ?? []

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['customers', customerId, 'addresses'] })
    onChanged()
  }

  const setDefaultMutation = useMutation({
    mutationFn: (addressId: string) => customerService.setDefaultAddress(customerId, addressId),
    onSuccess: () => { enqueueSnackbar('Delivery address updated', { variant: 'success' }); invalidate() },
    onError: () => enqueueSnackbar('Could not switch address. Please try again.', { variant: 'error' }),
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { addressLine1: '', addressLine2: '', city: '', state: '', pincode: '' },
  })

  const closeAddForm = () => { setAdding(false); reset(); resetLocation() }

  const onSubmit = async (values: FormValues) => {
    try {
      await customerService.addAddress({
        addressLine1: values.addressLine1.trim(),
        addressLine2: values.addressLine2?.trim() || undefined,
        city: values.city.trim(),
        state: values.state.trim(),
        pincode: values.pincode.trim(),
        latitude: coords?.latitude,
        longitude: coords?.longitude,
      })
      enqueueSnackbar('Address added', { variant: 'success' })
      closeAddForm()
      invalidate()
    } catch (err: any) {
      enqueueSnackbar(err.response?.data?.message ?? 'Could not save this address. Please try again.', { variant: 'error' })
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Delivery Address</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {isError && <Alert severity="error">Couldn't load your addresses.</Alert>}
        {isLoading && <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}><CircularProgress size={28} /></Box>}

        {!isLoading && !isError && addresses.length > 0 && (
          <RadioGroup
            value={addresses.find((a) => a.defaultAddress)?.id ?? ''}
            onChange={(e) => setDefaultMutation.mutate(e.target.value)}
          >
            {addresses.map((a) => (
              <Box key={a.id} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 1.5, mb: 1, display: 'flex', alignItems: 'flex-start' }}>
                <FormControlLabel
                  value={a.id}
                  disabled={setDefaultMutation.isPending}
                  control={<Radio size="small" />}
                  sx={{ alignItems: 'flex-start', m: 0, width: '100%', minWidth: 0 }}
                  label={
                    <Box sx={{ ml: 0.5, minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={600}>{a.addressLine1}</Typography>
                      <Typography variant="caption" color="text.secondary" display="block">
                        {[a.addressLine2, a.city, a.state, a.pincode].filter(Boolean).join(', ')}
                      </Typography>
                      {a.latitude == null || a.longitude == null ? (
                        <Chip
                          size="small" variant="outlined" icon={<LocationOff sx={{ fontSize: 14 }} />}
                          label="Location not set" sx={{ mt: 0.5, height: 20, fontSize: 11 }}
                        />
                      ) : (
                        <Chip
                          size="small" variant="outlined" color="success" icon={<CheckCircle sx={{ fontSize: 14 }} />}
                          label="Location set" sx={{ mt: 0.5, height: 20, fontSize: 11 }}
                        />
                      )}
                    </Box>
                  }
                />
                <Tooltip title="Edit address">
                  <IconButton size="small" aria-label="Edit address" onClick={() => setEditingAddress(a)} sx={{ flexShrink: 0 }}>
                    <Edit fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Box>
            ))}
          </RadioGroup>
        )}

        {!isLoading && !isError && addresses.length === 0 && !adding && (
          <Alert severity="info">You don't have a saved address yet.</Alert>
        )}

        {!adding ? (
          <Button startIcon={<Add />} onClick={() => setAdding(true)} sx={{ alignSelf: 'flex-start' }}>
            Add New Address
          </Button>
        ) : (
          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ mt: 1 }}>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <TextField
                label="Address Line 1" required fullWidth size="small"
                error={!!errors.addressLine1} helperText={errors.addressLine1?.message}
                {...register('addressLine1')}
              />
              <TextField
                label="Address Line 2" fullWidth size="small"
                helperText="Optional"
                {...register('addressLine2')}
              />
              <Box sx={{ display: 'flex', gap: 1.5 }}>
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
              {coords ? (
                <Alert severity="success" icon={<CheckCircle fontSize="small" />}>Location captured</Alert>
              ) : (
                <Button
                  onClick={capture} disabled={locating} size="small" variant="outlined"
                  startIcon={locating ? <CircularProgress size={16} /> : <MyLocation />}
                  sx={{ alignSelf: 'flex-start' }}
                >
                  {locating ? 'Locating…' : 'Use my current location'}
                </Button>
              )}
              {locationError && <Alert severity="warning">{locationError}</Alert>}
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 2 }}>
              <Button onClick={closeAddForm} color="inherit">Cancel</Button>
              <Button type="submit" variant="contained">Save Address</Button>
            </Box>
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} startIcon={<Close />}>Close</Button>
      </DialogActions>

      <EditAddressDialog
        open={Boolean(editingAddress)}
        customerId={customerId}
        address={editingAddress}
        onClose={() => setEditingAddress(null)}
        onSaved={() => {
          enqueueSnackbar('Address updated', { variant: 'success' })
          setEditingAddress(null)
          invalidate()
        }}
      />
    </Dialog>
  )
}
