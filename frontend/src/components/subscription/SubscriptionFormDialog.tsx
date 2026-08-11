import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, MenuItem, Box,
  CircularProgress, Alert, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import dayjs from 'dayjs'
import { CustomerAutocomplete } from './CustomerAutocomplete'
import { subscriptionService } from '../../services/subscriptionService'
import { MILK_TYPES, MILK_TYPE_LABELS, SCHEDULE_TYPES, SCHEDULE_TYPE_LABELS, DELIVERY_DAYS } from '../../types/subscription.types'
import type { DeliveryDay, Subscription } from '../../types/subscription.types'
import type { Customer } from '../../types/customer.types'

const schema = yup.object({
  milkType: yup.string().oneOf(MILK_TYPES).required('Milk type is required'),
  quantity: yup.number()
    .typeError('Quantity is required')
    .required('Quantity is required')
    .min(0.5, 'Minimum quantity is 0.5 litres')
    .max(10, 'Maximum quantity is 10 litres'),
  scheduleType: yup.string().oneOf(SCHEDULE_TYPES).required('Schedule is required'),
  startDate: yup.string().required('Start date is required'),
  endDate: yup.string(),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  subscription: Subscription | null
  // Only admin roles (SUPER_ADMIN/FARM_MANAGER) get the customer selector - a CUSTOMER can only
  // ever create/edit their own, and the backend ignores/ requires no customerId for them.
  canSelectCustomer: boolean
  onClose: () => void
  onSaved: () => void
}

export function SubscriptionFormDialog({ open, subscription, canSelectCustomer, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(subscription)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [customerError, setCustomerError] = useState('')
  const [deliveryDays, setDeliveryDays] = useState<DeliveryDay[]>([])
  const [deliveryDaysError, setDeliveryDaysError] = useState('')

  const { control, register, handleSubmit, reset, watch, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: {
      milkType: undefined, quantity: undefined, scheduleType: undefined,
      startDate: dayjs().format('YYYY-MM-DD'), endDate: '',
    },
  })
  const scheduleType = watch('scheduleType')

  useEffect(() => {
    if (!open) return
    setServerError(''); setCustomerError(''); setDeliveryDaysError(''); setCustomer(null)
    setDeliveryDays(subscription?.deliveryDays ?? [])
    reset({
      milkType: subscription?.milkType,
      quantity: subscription?.quantity,
      scheduleType: subscription?.scheduleType,
      startDate: subscription?.startDate ?? dayjs().format('YYYY-MM-DD'),
      endDate: subscription?.endDate ?? '',
    })
  }, [open, subscription, reset])

  const onSubmit = async (values: FormValues) => {
    setServerError(''); setCustomerError(''); setDeliveryDaysError('')

    if (!isEdit && canSelectCustomer && !customer) {
      setCustomerError('Select a customer')
      return
    }
    if (values.scheduleType === 'WEEKLY' && deliveryDays.length === 0) {
      setDeliveryDaysError('Select at least one delivery day for a weekly schedule')
      return
    }

    setSubmitting(true)
    try {
      if (isEdit && subscription) {
        await subscriptionService.update(subscription.id, {
          milkType: values.milkType as any,
          quantity: values.quantity,
          scheduleType: values.scheduleType as any,
          deliveryDays: values.scheduleType === 'WEEKLY' ? deliveryDays : undefined,
          endDate: values.endDate || undefined,
        })
        enqueueSnackbar('Subscription updated successfully', { variant: 'success' })
      } else {
        await subscriptionService.create({
          customerId: canSelectCustomer ? customer!.id : undefined,
          milkType: values.milkType as any,
          quantity: values.quantity,
          scheduleType: values.scheduleType as any,
          deliveryDays: values.scheduleType === 'WEEKLY' ? deliveryDays : undefined,
          startDate: values.startDate,
          endDate: values.endDate || undefined,
        })
        enqueueSnackbar('Subscription created successfully', { variant: 'success' })
      }
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Subscription' : 'New Subscription'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          {!isEdit && canSelectCustomer && (
            <CustomerAutocomplete value={customer} onChange={setCustomer} error={!!customerError} helperText={customerError} />
          )}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <Controller
              name="milkType"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field} value={field.value ?? ''}
                  select label="Milk Type" required fullWidth size="small"
                  error={!!errors.milkType} helperText={errors.milkType?.message}
                >
                  {MILK_TYPES.map((m) => <MenuItem key={m} value={m}>{MILK_TYPE_LABELS[m]}</MenuItem>)}
                </TextField>
              )}
            />
            <TextField
              label="Quantity (L)" type="number" required fullWidth size="small"
              inputProps={{ step: '0.5', min: 0.5, max: 10 }}
              error={!!errors.quantity} helperText={errors.quantity?.message ?? '0.5 - 10 litres'}
              {...register('quantity')}
            />
          </Box>

          <Controller
            name="scheduleType"
            control={control}
            render={({ field }) => (
              <TextField
                {...field} value={field.value ?? ''}
                select label="Delivery Schedule" required fullWidth size="small"
                error={!!errors.scheduleType} helperText={errors.scheduleType?.message}
              >
                {SCHEDULE_TYPES.map((s) => <MenuItem key={s} value={s}>{SCHEDULE_TYPE_LABELS[s]}</MenuItem>)}
              </TextField>
            )}
          />

          {scheduleType === 'WEEKLY' && (
            <Box>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                Delivery Days
              </Typography>
              <ToggleButtonGroup
                value={deliveryDays} onChange={(_e, days) => setDeliveryDays(days)}
                size="small" color="primary"
              >
                {DELIVERY_DAYS.map((d) => <ToggleButton key={d} value={d}>{d}</ToggleButton>)}
              </ToggleButtonGroup>
              {deliveryDaysError && <Typography variant="caption" color="error" display="block">{deliveryDaysError}</Typography>}
            </Box>
          )}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Start Date" type="date" required fullWidth size="small"
              disabled={isEdit}
              InputLabelProps={{ shrink: true }}
              error={!!errors.startDate} helperText={isEdit ? 'Cannot be changed' : errors.startDate?.message}
              {...register('startDate')}
            />
            <TextField
              label="End Date" type="date" fullWidth size="small"
              InputLabelProps={{ shrink: true }}
              error={!!errors.endDate} helperText={errors.endDate?.message ?? 'Optional'}
              {...register('endDate')}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Create Subscription'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
