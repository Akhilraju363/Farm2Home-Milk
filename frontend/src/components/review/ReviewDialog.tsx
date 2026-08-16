import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, MenuItem,
  Box, Typography, CircularProgress, Alert, Rating,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { useQuery } from '@tanstack/react-query'
import { productService } from '../../services/productService'
import { reviewService } from '../../services/reviewService'
import type { Review } from '../../types/review.types'

const schema = yup.object({
  productId: yup.string().required('Product is required'),
  rating: yup.number().typeError('Rating is required').required('Rating is required').min(1, 'Rating is required').max(5),
  reviewText: yup.string().max(2000, 'Review is too long (max 2000 characters)'),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  // Create mode: rate a product from this delivered order. Edit mode: pass the existing review
  // instead - orderId/productId are then fixed and only rating/text can change.
  orderId?: string
  review?: Review | null
  onClose: () => void
  onSaved: () => void
}

export function ReviewDialog({ open, orderId, review, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(review)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  // Only fetched in create mode - editing a review never changes which product it's for.
  const { data: productsRes, isLoading: productsLoading } = useQuery({
    queryKey: ['products', 'active', 'review-form'],
    queryFn: () => productService.search({ active: true, size: 100 }),
    enabled: open && !isEdit,
  })
  const products = productsRes?.data.data.content ?? []

  const {
    control, register, handleSubmit, reset, formState: { errors, isSubmitting: formSubmitting },
  } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { productId: '', rating: 0, reviewText: '' },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    reset({
      productId: review?.productId ?? '',
      rating: review?.rating ?? 0,
      reviewText: review?.reviewText ?? '',
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, review])

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      if (isEdit && review) {
        await reviewService.update(review.id, { rating: values.rating, reviewText: values.reviewText?.trim() || undefined })
      } else if (orderId) {
        await reviewService.create({
          orderId, productId: values.productId, rating: values.rating, reviewText: values.reviewText?.trim() || undefined,
        })
      }
      enqueueSnackbar(isEdit ? 'Review updated successfully' : 'Review submitted successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const busy = submitting || formSubmitting

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Your Review' : 'Rate This Product'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          {isEdit ? (
            <Typography variant="body2" color="text.secondary">{review?.productName}</Typography>
          ) : (
            <Controller
              name="productId"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  select label="Product" required fullWidth size="small"
                  disabled={productsLoading}
                  error={!!errors.productId} helperText={errors.productId?.message ?? (productsLoading ? 'Loading products…' : ' ')}
                >
                  {products.map((p) => (
                    <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>
                  ))}
                </TextField>
              )}
            />
          )}

          <Box>
            <Typography variant="caption" color="text.secondary" display="block" gutterBottom>Rating</Typography>
            <Controller
              name="rating"
              control={control}
              render={({ field }) => (
                <Rating {...field} value={field.value || 0} onChange={(_, v) => field.onChange(v ?? 0)} size="large" />
              )}
            />
            {errors.rating && <Typography variant="caption" color="error" display="block">{errors.rating.message}</Typography>}
          </Box>

          <TextField
            label="Your Review (optional)" fullWidth multiline minRows={3} size="small"
            error={!!errors.reviewText} helperText={errors.reviewText?.message}
            {...register('reviewText')}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={busy} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy}>
            {busy ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Submit Review'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
