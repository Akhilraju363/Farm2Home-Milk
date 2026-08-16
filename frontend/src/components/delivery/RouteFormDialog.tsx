import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress, Alert,
  Divider, Typography,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { deliveryRouteService } from '../../services/deliveryService'
import type { DeliveryRoute } from '../../types/delivery.types'

const schema = yup.object({
  routeName: yup.string().trim().required('Route name is required').max(100, 'Max 100 characters'),
  routeCode: yup.string().trim().required('Route code is required').max(20, 'Max 20 characters'),
  area: yup.string().trim().required('Area is required').max(100, 'Max 100 characters'),
  city: yup.string().trim().required('City is required').max(100, 'Max 100 characters'),
  pincode: yup.string().trim().required('Pincode is required')
    .min(6, 'Pincode must be between 6 and 10 characters').max(10, 'Pincode must be between 6 and 10 characters'),
  // All three optional together - a route with no coverage circle is simply never chosen by
  // automatic selection (see DeliveryRouteSelectionServiceImpl), but remains fully usable for
  // manual assignment. Backend enforces the same numeric ranges (DecimalMin/Max/Positive).
  centerLatitude: yup.number().transform((v, o) => (o === '' ? undefined : v))
    .min(-90, 'Must be between -90 and 90').max(90, 'Must be between -90 and 90').nullable(),
  centerLongitude: yup.number().transform((v, o) => (o === '' ? undefined : v))
    .min(-180, 'Must be between -180 and 180').max(180, 'Must be between -180 and 180').nullable(),
  radiusKm: yup.number().transform((v, o) => (o === '' ? undefined : v))
    .moreThan(0, 'Must be greater than 0').nullable(),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  route: DeliveryRoute | null
  onClose: () => void
  onSaved: () => void
}

// routeCode cannot be changed once created (matches UpdateRouteRequest on the backend, which has
// no routeCode field) - the field is disabled, not hidden, in edit mode so the value stays visible.
export function RouteFormDialog({ open, route, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(route)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: {
      routeName: '', routeCode: '', area: '', city: '', pincode: '',
      centerLatitude: undefined, centerLongitude: undefined, radiusKm: undefined,
    },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    reset({
      routeName: route?.routeName ?? '',
      routeCode: route?.routeCode ?? '',
      area: route?.area ?? '',
      city: route?.city ?? '',
      pincode: route?.pincode ?? '',
      centerLatitude: route?.centerLatitude ?? undefined,
      centerLongitude: route?.centerLongitude ?? undefined,
      radiusKm: route?.radiusKm ?? undefined,
    })
  }, [open, route, reset])

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      if (isEdit && route) {
        await deliveryRouteService.update(route.id, {
          routeName: values.routeName.trim(),
          area: values.area.trim(),
          city: values.city.trim(),
          pincode: values.pincode.trim(),
          centerLatitude: values.centerLatitude ?? undefined,
          centerLongitude: values.centerLongitude ?? undefined,
          radiusKm: values.radiusKm ?? undefined,
        })
      } else {
        await deliveryRouteService.create({
          routeName: values.routeName.trim(),
          routeCode: values.routeCode.trim(),
          area: values.area.trim(),
          city: values.city.trim(),
          pincode: values.pincode.trim(),
          centerLatitude: values.centerLatitude ?? undefined,
          centerLongitude: values.centerLongitude ?? undefined,
          radiusKm: values.radiusKm ?? undefined,
        })
      }
      enqueueSnackbar(isEdit ? 'Route updated successfully' : 'Route created successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Route' : 'Create Route'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Route Name" required fullWidth size="small" autoFocus
              error={!!errors.routeName} helperText={errors.routeName?.message}
              {...register('routeName')}
            />
            <TextField
              label="Route Code" required fullWidth size="small"
              disabled={isEdit}
              error={!!errors.routeCode} helperText={errors.routeCode?.message ?? (isEdit ? 'Cannot be changed' : undefined)}
              {...register('routeCode')}
            />
          </Box>
          <TextField
            label="Area" required fullWidth size="small"
            error={!!errors.area} helperText={errors.area?.message}
            {...register('area')}
          />
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="City" required fullWidth size="small"
              error={!!errors.city} helperText={errors.city?.message}
              {...register('city')}
            />
            <TextField
              label="Pincode" required fullWidth size="small"
              error={!!errors.pincode} helperText={errors.pincode?.message}
              {...register('pincode')}
            />
          </Box>

          <Divider sx={{ my: 0.5 }} />
          <Typography variant="caption" color="text.secondary">
            Coverage circle used to automatically select this route for a customer's delivery address.
            Leave blank if this route shouldn't be chosen automatically.
          </Typography>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Center Latitude" type="number" fullWidth size="small"
              inputProps={{ step: 'any' }}
              error={!!errors.centerLatitude} helperText={errors.centerLatitude?.message}
              {...register('centerLatitude')}
            />
            <TextField
              label="Center Longitude" type="number" fullWidth size="small"
              inputProps={{ step: 'any' }}
              error={!!errors.centerLongitude} helperText={errors.centerLongitude?.message}
              {...register('centerLongitude')}
            />
            <TextField
              label="Coverage Radius (km)" type="number" fullWidth size="small"
              inputProps={{ step: 'any', min: 0 }}
              error={!!errors.radiusKm} helperText={errors.radiusKm?.message}
              {...register('radiusKm')}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Create Route'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
