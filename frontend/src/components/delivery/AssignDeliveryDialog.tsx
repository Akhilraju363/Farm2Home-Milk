import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress,
  Alert, Autocomplete, Typography, Chip,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { useDebounced } from '../../hooks/useDebounced'
import { orderService } from '../../services/orderService'
import { deliveryPartnerService, deliveryRouteService, deliveryService } from '../../services/deliveryService'
import { ORDER_TYPE_LABELS } from '../../types/order.types'
import type { Order } from '../../types/order.types'
import type { Partner, DeliveryRoute } from '../../types/delivery.types'

const DEBOUNCE_MS = 400
const MIN_QUERY_LENGTH = 2

interface Props {
  open: boolean
  presetOrder?: Order | null
  onClose: () => void
  onAssigned: () => void
}

// FARM_MANAGER/SUPER_ADMIN only - the caller must already be gated to canAssign before rendering
// this. There's no "list unassigned orders" endpoint anywhere in this backend (delivery-service
// doesn't know about orders it hasn't been told about, order-service doesn't know about
// assignments at all), so this searches ALL orders by keyword; if the chosen order already has an
// assignment, POST /delivery/assignments rejects it and the real server error is surfaced.
export function AssignDeliveryDialog({ open, presetOrder, onClose, onAssigned }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const [order, setOrder] = useState<Order | null>(null)
  const [orderInput, setOrderInput] = useState('')
  const [orderError, setOrderError] = useState('')

  const [partner, setPartner] = useState<Partner | null>(null)
  const [partnerError, setPartnerError] = useState('')

  const [route, setRoute] = useState<DeliveryRoute | null>(null)
  const [routeError, setRouteError] = useState('')

  const debouncedOrderInput = useDebounced(orderInput, DEBOUNCE_MS)
  const orderSearchReady = debouncedOrderInput.trim().length >= MIN_QUERY_LENGTH

  const { data: orderData, isFetching: ordersLoading } = useQuery({
    queryKey: ['orders', 'search', 'assign-autocomplete', debouncedOrderInput],
    queryFn: () => orderService.search({ keyword: debouncedOrderInput.trim(), size: 8 }),
    enabled: orderSearchReady && !presetOrder,
  })
  const orderOptions = orderSearchReady ? (orderData?.data.data.content ?? []) : []

  const { data: partnerData, isFetching: partnersLoading } = useQuery({
    queryKey: ['delivery', 'partners', 'assign-select'],
    queryFn: () => deliveryPartnerService.search({ size: 100 }),
    enabled: open,
  })
  const partners = partnerData?.data.data.content ?? []

  const { data: routeData, isFetching: routesLoading } = useQuery({
    queryKey: ['delivery', 'routes', 'assign-select'],
    queryFn: () => deliveryRouteService.search({ size: 100 }),
    enabled: open,
  })
  const routes = routeData?.data.data.content ?? []

  useEffect(() => {
    if (!open) return
    setServerError(''); setOrderError(''); setPartnerError(''); setRouteError('')
    setOrder(presetOrder ?? null)
    setOrderInput('')
    setPartner(null)
    setRoute(null)
  }, [open, presetOrder])

  const handleSubmit = async () => {
    setServerError(''); setOrderError(''); setPartnerError(''); setRouteError('')
    let hasError = false
    if (!order) { setOrderError('Select an order'); hasError = true }
    if (!partner) { setPartnerError('Select a delivery partner'); hasError = true }
    if (!route) { setRouteError('Select a route'); hasError = true }
    if (hasError) return

    setSubmitting(true)
    try {
      await deliveryService.assign({ orderId: order!.id, deliveryPartnerId: partner!.id, routeId: route!.id })
      enqueueSnackbar('Delivery assigned successfully', { variant: 'success' })
      onAssigned()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Could not assign this delivery. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>Assign Delivery</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {serverError && <Alert severity="error">{serverError}</Alert>}

        {presetOrder ? (
          <TextField label="Order" value={presetOrder.orderNumber} disabled fullWidth size="small" />
        ) : (
          <Autocomplete
            value={order}
            onChange={(_e, v) => setOrder(v)}
            inputValue={orderInput}
            onInputChange={(_e, v) => setOrderInput(v)}
            options={orderOptions}
            loading={ordersLoading}
            getOptionLabel={(o) => o.orderNumber}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            noOptionsText={orderSearchReady ? 'No matching orders' : 'Type at least 2 characters to search'}
            renderOption={(props, o) => (
              <Box component="li" {...props} key={o.id}>
                <Box>
                  <Typography variant="body2">{o.orderNumber}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {ORDER_TYPE_LABELS[o.orderType]} • {o.status} • {o.orderDate}
                  </Typography>
                </Box>
              </Box>
            )}
            renderInput={(params) => (
              <TextField
                {...params} label="Order" required size="small"
                error={!!orderError} helperText={orderError}
                InputProps={{
                  ...params.InputProps,
                  endAdornment: <>{ordersLoading && <CircularProgress size={16} />}{params.InputProps.endAdornment}</>,
                }}
              />
            )}
          />
        )}

        <Autocomplete
          value={partner}
          onChange={(_e, v) => setPartner(v)}
          options={partners}
          loading={partnersLoading}
          getOptionLabel={(p) => p.name}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          renderOption={(props, p) => (
            <Box component="li" {...props} key={p.id}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%' }}>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="body2">{p.name}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {p.mobile}{p.vehicleType ? ` • ${p.vehicleType}` : ''}
                  </Typography>
                </Box>
                <Chip label={p.active ? 'Active' : 'Inactive'} size="small" color={p.active ? 'success' : 'default'} />
              </Box>
            </Box>
          )}
          renderInput={(params) => (
            <TextField
              {...params} label="Delivery Partner" required size="small"
              error={!!partnerError} helperText={partnerError}
              InputProps={{
                ...params.InputProps,
                endAdornment: <>{partnersLoading && <CircularProgress size={16} />}{params.InputProps.endAdornment}</>,
              }}
            />
          )}
        />

        <Autocomplete
          value={route}
          onChange={(_e, v) => setRoute(v)}
          options={routes}
          loading={routesLoading}
          getOptionLabel={(r) => `${r.routeCode} — ${r.area}, ${r.city}`}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          renderInput={(params) => (
            <TextField
              {...params} label="Route" required size="small"
              error={!!routeError} helperText={routeError}
              InputProps={{
                ...params.InputProps,
                endAdornment: <>{routesLoading && <CircularProgress size={16} />}{params.InputProps.endAdornment}</>,
              }}
            />
          )}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={submitting}>
          {submitting ? <CircularProgress size={20} color="inherit" /> : 'Assign'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
