import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField,
  Box, Typography, CircularProgress, Avatar, Alert,
} from '@mui/material'
import { PhotoCamera, Agriculture, Close } from '@mui/icons-material'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { farmService } from '../../services/farmService'
import type { Farm } from '../../types/farm.types'

const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const schema = yup.object({
  farmName: yup.string().trim().required('Farm name is required').max(150, 'Max 150 characters'),
  ownerName: yup.string().trim().required('Owner name is required').max(150, 'Max 150 characters'),
  location: yup.string().max(255, 'Max 255 characters'),
  description: yup.string(),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  farm: Farm | null
  onClose: () => void
  onSaved: () => void
}

export function FarmFormDialog({ open, farm, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(farm)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [imageError, setImageError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { farmName: '', ownerName: '', location: '', description: '' },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    setImageFile(null)
    setImageError('')
    setImagePreview(farm?.imageUrl ?? null)
    reset({
      farmName: farm?.farmName ?? '',
      ownerName: farm?.ownerName ?? '',
      location: farm?.location ?? '',
      description: farm?.description ?? '',
    })
  }, [open, farm, reset])

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setImageError('')
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      setImageError('Only JPEG, PNG, or WEBP images are allowed.')
      return
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      setImageError('Image must be smaller than 5 MB.')
      return
    }
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      const payload = {
        farmName: values.farmName.trim(),
        ownerName: values.ownerName.trim(),
        location: values.location?.trim() || undefined,
        description: values.description?.trim() || undefined,
      }

      let farmId = farm?.id
      if (isEdit && farm) {
        await farmService.update(farm.id, payload)
      } else {
        const created = await farmService.create(payload)
        farmId = created.data.data.id
      }

      if (imageFile && farmId) {
        await farmService.uploadImage(farmId, imageFile)
      }

      enqueueSnackbar(isEdit ? 'Farm updated successfully' : 'Farm registered successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Farm' : 'Register Farm'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar
              src={imagePreview ?? undefined}
              variant="rounded"
              sx={{ width: 72, height: 72, bgcolor: 'action.hover' }}
            >
              <Agriculture color="disabled" />
            </Avatar>
            <Box>
              <Button
                size="small"
                variant="outlined"
                startIcon={<PhotoCamera />}
                onClick={() => fileInputRef.current?.click()}
              >
                {imagePreview ? 'Replace Image' : 'Upload Image'}
              </Button>
              <input ref={fileInputRef} type="file" accept={ACCEPTED_IMAGE_TYPES.join(',')} hidden onChange={handleFileSelect} />
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                JPEG, PNG, or WEBP. Max 5 MB.
              </Typography>
              {imageError && <Typography variant="caption" color="error" display="block">{imageError}</Typography>}
            </Box>
          </Box>

          <TextField
            label="Farm Name" required fullWidth size="small" autoFocus
            error={!!errors.farmName} helperText={errors.farmName?.message}
            {...register('farmName')}
          />
          <TextField
            label="Owner Name" required fullWidth size="small"
            error={!!errors.ownerName} helperText={errors.ownerName?.message}
            {...register('ownerName')}
          />
          <TextField
            label="Location" fullWidth size="small"
            error={!!errors.location} helperText={errors.location?.message}
            {...register('location')}
          />
          <TextField
            label="Description" fullWidth multiline minRows={2} size="small"
            error={!!errors.description} helperText={errors.description?.message}
            {...register('description')}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Register Farm'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
