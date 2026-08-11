import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress, Alert } from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import dayjs from 'dayjs'
import { useSnackbar } from 'notistack'
import { subscriptionService } from '../../services/subscriptionService'

const schema = yup.object({
  pauseEnd: yup.string()
    .required('Pause end date is required')
    .test('is-future', 'Pause end date must be a future date', (v) => Boolean(v) && dayjs(v).isAfter(dayjs(), 'day')),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  subscriptionId: string | null
  onClose: () => void
  onSaved: () => void
}

export function PauseSubscriptionDialog({ open, subscriptionId, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { pauseEnd: dayjs().add(7, 'day').format('YYYY-MM-DD') },
  })

  useEffect(() => {
    if (open) { setServerError(''); reset({ pauseEnd: dayjs().add(7, 'day').format('YYYY-MM-DD') }) }
  }, [open, reset])

  const onSubmit = async (values: FormValues) => {
    if (!subscriptionId) return
    setServerError('')
    setSubmitting(true)
    try {
      await subscriptionService.pause(subscriptionId, { pauseEnd: values.pauseEnd })
      enqueueSnackbar('Subscription paused successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Could not pause the subscription. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Pause Subscription?</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <Alert severity="info" sx={{ fontSize: 13 }}>
            Deliveries stop until the date below, then the subscription automatically resumes.
          </Alert>
          <TextField
            label="Resume On" type="date" required fullWidth size="small"
            InputLabelProps={{ shrink: true }}
            error={!!errors.pauseEnd} helperText={errors.pauseEnd?.message}
            {...register('pauseEnd')}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" color="warning" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : 'Pause Subscription'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
