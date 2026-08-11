import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, MenuItem, Box,
  CircularProgress, Alert, IconButton, Typography, Divider,
} from '@mui/material'
import { Close, Add, DeleteOutline } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import dayjs from 'dayjs'
import { useSnackbar } from 'notistack'
import { CustomerAutocomplete } from '../subscription/CustomerAutocomplete'
import { orderService } from '../../services/orderService'
import { MILK_TYPES, MILK_TYPE_LABELS } from '../../types/order.types'
import type { MilkType } from '../../types/order.types'
import type { Customer } from '../../types/customer.types'

interface ItemRow {
  milkType: MilkType | ''
  quantity: string
}

const schema = yup.object({
  orderDate: yup.string()
    .required('Order date is required')
    .test('not-past', 'Order date cannot be in the past', (v) => Boolean(v) && !dayjs(v).isBefore(dayjs(), 'day')),
  notes: yup.string(),
})

type FormValues = yup.InferType<typeof schema>

const emptyRow: ItemRow = { milkType: '', quantity: '' }

interface Props {
  open: boolean
  // Only admin roles get the customer selector - a CUSTOMER can only ever order for themselves,
  // and the backend ignores/requires no customerId for them (see OrderController.create).
  canSelectCustomer: boolean
  onClose: () => void
  onSaved: () => void
}

export function NewOrderDialog({ open, canSelectCustomer, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [customerError, setCustomerError] = useState('')
  const [items, setItems] = useState<ItemRow[]>([{ ...emptyRow }])
  const [itemsError, setItemsError] = useState('')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { orderDate: dayjs().format('YYYY-MM-DD'), notes: '' },
  })

  useEffect(() => {
    if (!open) return
    setServerError(''); setCustomerError(''); setItemsError('')
    setCustomer(null)
    setItems([{ ...emptyRow }])
    reset({ orderDate: dayjs().format('YYYY-MM-DD'), notes: '' })
  }, [open, reset])

  const updateItem = (index: number, patch: Partial<ItemRow>) => {
    setItems((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }
  const addItem = () => setItems((prev) => [...prev, { ...emptyRow }])
  const removeItem = (index: number) => setItems((prev) => prev.filter((_, i) => i !== index))

  const onSubmit = async (values: FormValues) => {
    setServerError(''); setCustomerError(''); setItemsError('')

    if (canSelectCustomer && !customer) {
      setCustomerError('Select a customer')
      return
    }
    if (items.length === 0) {
      setItemsError('Add at least one item')
      return
    }
    for (const row of items) {
      const qty = Number(row.quantity)
      if (!row.milkType || !row.quantity || Number.isNaN(qty) || qty < 0.5 || qty > 10) {
        setItemsError('Every item needs a milk type and a quantity between 0.5 and 10 litres')
        return
      }
    }

    setSubmitting(true)
    try {
      await orderService.create({
        customerId: canSelectCustomer ? customer!.id : undefined,
        orderDate: values.orderDate,
        notes: values.notes || undefined,
        items: items.map((row) => ({ milkType: row.milkType as MilkType, quantity: Number(row.quantity) })),
      })
      enqueueSnackbar('Order created successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>New Order</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          {canSelectCustomer && (
            <CustomerAutocomplete value={customer} onChange={setCustomer} error={!!customerError} helperText={customerError} />
          )}

          <TextField
            label="Order Date" type="date" required fullWidth size="small"
            InputLabelProps={{ shrink: true }}
            error={!!errors.orderDate} helperText={errors.orderDate?.message ?? "Today's order is checked against today's milk production capacity"}
            {...register('orderDate')}
          />

          <TextField
            label="Notes" fullWidth size="small" multiline minRows={2}
            placeholder="e.g. Please leave at the door"
            {...register('notes')}
          />

          <Divider />

          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
              <Typography variant="subtitle2" fontWeight={700}>Items</Typography>
              <Button size="small" startIcon={<Add />} onClick={addItem}>Add Item</Button>
            </Box>

            {items.map((row, index) => (
              <Box key={index} sx={{ display: 'flex', gap: 1, mb: 1, alignItems: 'flex-start' }}>
                <TextField
                  select label="Milk Type" required size="small" sx={{ flex: 1 }}
                  value={row.milkType}
                  onChange={(e) => updateItem(index, { milkType: e.target.value as MilkType })}
                >
                  {MILK_TYPES.map((m) => <MenuItem key={m} value={m}>{MILK_TYPE_LABELS[m]}</MenuItem>)}
                </TextField>
                <TextField
                  label="Quantity (L)" type="number" required size="small" sx={{ width: 130 }}
                  inputProps={{ step: '0.5', min: 0.5, max: 10 }}
                  value={row.quantity}
                  onChange={(e) => updateItem(index, { quantity: e.target.value })}
                />
                <IconButton
                  size="small" onClick={() => removeItem(index)} disabled={items.length === 1}
                  sx={{ mt: 0.5 }}
                >
                  <DeleteOutline fontSize="small" />
                </IconButton>
              </Box>
            ))}
            {itemsError && <Typography variant="caption" color="error" display="block">{itemsError}</Typography>}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : 'Create Order'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
