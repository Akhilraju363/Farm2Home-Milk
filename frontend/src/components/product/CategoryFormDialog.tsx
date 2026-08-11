import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress, Switch, FormControlLabel, Alert } from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { productCategoryService } from '../../services/productCategoryService'
import type { ProductCategory } from '../../types/product.types'

const schema = yup.object({
  name: yup.string().trim().required('Category name is required').max(50, 'Max 50 characters'),
  description: yup.string().max(2000, 'Description is too long'),
  active: yup.boolean().default(true),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  category: ProductCategory | null
  onClose: () => void
  onSaved: () => void
}

export function CategoryFormDialog({ open, category, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(category)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { control, register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { name: '', description: '', active: true },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    reset({ name: category?.name ?? '', description: category?.description ?? '', active: category?.active ?? true })
  }, [open, category, reset])

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      const payload = { name: values.name.trim(), description: values.description?.trim() || undefined }
      if (isEdit && category) {
        await productCategoryService.update(category.id, { ...payload, active: values.active })
      } else {
        await productCategoryService.create(payload)
      }
      enqueueSnackbar(isEdit ? 'Category updated successfully' : 'Category created successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Category' : 'Add Category'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <TextField
            label="Category Name" required fullWidth size="small" autoFocus
            error={!!errors.name} helperText={errors.name?.message}
            {...register('name')}
          />
          <TextField
            label="Description" fullWidth multiline minRows={2} size="small"
            error={!!errors.description} helperText={errors.description?.message}
            {...register('description')}
          />
          {isEdit && (
            <Controller
              name="active"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />}
                  label={field.value ? 'Active' : 'Inactive'}
                />
              )}
            />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Create Category'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
