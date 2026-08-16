import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField,
  Box, Typography, CircularProgress, Alert,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { useSnackbar } from 'notistack'
import { orderService } from '../../services/orderService'
import { formatCurrency } from '../../utils/formatters'
import { PRODUCT_UNIT_LABELS } from '../../types/product.types'
import type { Product } from '../../types/product.types'

const schema = yup.object({
  orderDate: yup.string().required('Delivery date is required'),
  quantity: yup.number()
    .typeError('Quantity is required')
    .required('Quantity is required')
    .moreThan(0, 'Quantity must be greater than 0')
    .max(50, 'Maximum quantity is 50'),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  product: Product | null
  onClose: () => void
}

/** Places a real ONE_TIME order for a single catalog product - see order-service's
 *  CreateOrderItemRequest, which now accepts productId as an alternative to milkType (price is
 *  always resolved server-side from the product's own current price, never sent from here). This
 *  is a direct single-item purchase, not a multi-item cart - there is no cart backend (see
 *  ShopProductCard/ShopProductDetailsPage), so the UI doesn't pretend to be one either. */
export function BuyNowDialog({ open, product, onClose }: Props) {
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const {
    register, handleSubmit, watch, reset, formState: { errors },
  } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { orderDate: dayjs().format('YYYY-MM-DD'), quantity: 1 },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    reset({ orderDate: dayjs().format('YYYY-MM-DD'), quantity: 1 })
  }, [open, reset])

  const quantity = watch('quantity')
  const estimatedTotal = product && quantity ? product.price * Number(quantity) : undefined

  const onSubmit = async (values: FormValues) => {
    if (!product) return
    setServerError('')
    setSubmitting(true)
    try {
      const res = await orderService.create({
        orderDate: values.orderDate,
        items: [{ productId: product.id, quantity: values.quantity }],
      })
      enqueueSnackbar('Order placed successfully', { variant: 'success' })
      onClose()
      navigate(`/orders/${res.data.data.id}`)
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Could not place the order. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!product) return null

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Buy Now</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          <Typography variant="subtitle1" fontWeight={700}>{product.name}</Typography>
          <Typography variant="body2" color="text.secondary">
            {formatCurrency(product.price)} / {PRODUCT_UNIT_LABELS[product.unit]}
          </Typography>

          <TextField
            label="Quantity" type="number" required fullWidth size="small"
            inputProps={{ min: 1, max: 50, step: 1 }}
            error={!!errors.quantity} helperText={errors.quantity?.message}
            {...register('quantity')}
          />

          <TextField
            label="Delivery Date" type="date" required fullWidth size="small"
            InputLabelProps={{ shrink: true }}
            inputProps={{ min: dayjs().format('YYYY-MM-DD') }}
            error={!!errors.orderDate} helperText={errors.orderDate?.message}
            {...register('orderDate')}
          />

          {estimatedTotal !== undefined && (
            <Typography variant="body2" color="text.secondary">
              Estimated total: <strong>{formatCurrency(estimatedTotal)}</strong>
            </Typography>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : 'Place Order'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
