import { Box, TextField, MenuItem, Button, Typography, Alert, CircularProgress } from '@mui/material'
import { useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { LegalPageLayout } from '../../components/legal/LegalPageLayout'
import { useAuth } from '../../hooks/useAuth'
import { dataRightsService } from '../../services/dataRightsService'
import { DATA_RIGHTS_REQUEST_TYPE_LABELS } from '../../types/dataRights.types'
import type { DataRightsRequestType } from '../../types/dataRights.types'
import { GRIEVANCE_OFFICER } from '../../constants/legal'

const REQUEST_TYPES = Object.keys(DATA_RIGHTS_REQUEST_TYPE_LABELS) as DataRightsRequestType[]

const schema = yup.object({
  requesterName: yup.string().trim().required('Name is required').max(150),
  requesterContact: yup.string().trim().required('An email or mobile number is required').max(150),
  requestType: yup.string().oneOf(REQUEST_TYPES).required('Please select a request type'),
  details: yup.string().max(4000, 'Max 4000 characters'),
})

type FormValues = yup.InferType<typeof schema>

/** Public - no login required (see dataRightsService.submit / customer-service's public
 *  /data-rights-requests/submit). This is the DPDP-required channel for a data principal to
 *  exercise their access/correction/erasure/withdraw-consent rights or raise a general grievance,
 *  and must work even for someone who never created a Farm2Home account. */
export function DataRightsRequestPage() {
  const { user, isAuthenticated } = useAuth()
  const [submitted, setSubmitted] = useState(false)
  const [serverError, setServerError] = useState('')

  const {
    control, register, handleSubmit, formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { requesterContact: isAuthenticated ? (user?.email ?? user?.mobile ?? '') : '' },
  })

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    try {
      await dataRightsService.submit(values)
      setSubmitted(true)
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again, or email us directly.')
    }
  }

  return (
    <LegalPageLayout title="Data Rights Request">
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Use this form to request access to, correction of, or erasure of your personal data, to
        withdraw consent you previously gave, or to raise a general privacy grievance. You do not
        need a Farm2Home account to submit this - if you'd rather email us directly, you can reach
        our Grievance Officer at {GRIEVANCE_OFFICER.email}.
      </Typography>

      {submitted ? (
        <Alert severity="success">
          Thank you - your request has been received. We'll respond to the contact details you
          provided as soon as possible.
        </Alert>
      ) : (
        <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          <TextField
            label="Your Name" required fullWidth size="small"
            error={!!errors.requesterName} helperText={errors.requesterName?.message}
            {...register('requesterName')}
          />
          <TextField
            label="Email or Mobile Number" required fullWidth size="small"
            error={!!errors.requesterContact} helperText={errors.requesterContact?.message}
            {...register('requesterContact')}
          />
          <Controller
            name="requestType"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                value={field.value ?? ''}
                select label="Request Type" required fullWidth size="small"
                error={!!errors.requestType} helperText={errors.requestType?.message}
              >
                {REQUEST_TYPES.map((t) => (
                  <MenuItem key={t} value={t}>{DATA_RIGHTS_REQUEST_TYPE_LABELS[t]}</MenuItem>
                ))}
              </TextField>
            )}
          />
          <TextField
            label="Details (optional)" fullWidth multiline minRows={3} size="small"
            error={!!errors.details} helperText={errors.details?.message}
            {...register('details')}
          />

          <Box>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              {isSubmitting ? <CircularProgress size={20} color="inherit" /> : 'Submit Request'}
            </Button>
          </Box>
        </Box>
      )}
    </LegalPageLayout>
  )
}
