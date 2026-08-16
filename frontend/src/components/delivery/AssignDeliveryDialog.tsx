import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress,
  Alert, Autocomplete, Typography, Chip, Link as MuiLink, Divider,
} from '@mui/material'
import { Close, AltRoute, CheckCircle, SmartToy, Person } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { useAuth } from '../../hooks/useAuth'
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
  const { user } = useAuth()
  const navigate = useNavigate()
  // Route Management is FARM_MANAGER/SUPER_ADMIN only (DeliveryRouteController) - same pair that
  // can open this dialog at all (manualAssign's own @PreAuthorize), so in practice this is
  // always true here, but checked explicitly rather than assumed.
  const canManageRoutes = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const [order, setOrder] = useState<Order | null>(null)
  const [orderInput, setOrderInput] = useState('')
  const [orderError, setOrderError] = useState('')

  const [partner, setPartner] = useState<Partner | null>(null)
  const [partnerError, setPartnerError] = useState('')

  const [route, setRoute] = useState<DeliveryRoute | null>(null)
  const [routeError, setRouteError] = useState('')
  // The order's own automatically-selected route is authoritative by default (see
  // OrderServiceImpl.verifyDeliveryEligibility / DeliveryRouteSelectionServiceImpl) - the manual
  // dropdown only appears once an admin deliberately asks to override it, or when the order has
  // no auto-selected route at all (an old order, or a genuine route-coverage gap).
  const [overridingRoute, setOverridingRoute] = useState(false)

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

  // active: true - an inactive route must never be selectable here (DeliveryAssignmentServiceImpl
  // itself also rejects one, but this keeps a customer-facing admin from even seeing the option).
  const { data: routeData, isFetching: routesLoading } = useQuery({
    queryKey: ['delivery', 'routes', 'assign-select'],
    queryFn: () => deliveryRouteService.search({ active: true, size: 100 }),
    enabled: open,
  })
  const routes = routeData?.data.data.content ?? []
  const noActiveRoutes = open && !routesLoading && routes.length === 0

  // The order's own auto-selected route (see Order.deliveryRouteId) - resolved here, not
  // recalculated, since order-service/customer-service already did the real work.
  const { data: autoRouteData, isFetching: autoRouteLoading } = useQuery({
    queryKey: ['delivery', 'routes', 'auto-selected', order?.deliveryRouteId],
    queryFn: () => deliveryRouteService.getById(order!.deliveryRouteId!),
    enabled: open && Boolean(order?.deliveryRouteId),
  })
  const autoRoute = autoRouteData?.data.data
  const hasAutoRoute = Boolean(order?.deliveryRouteId)
  const showManualRoutePicker = overridingRoute || (Boolean(order) && !hasAutoRoute && !autoRouteLoading)

  // An order may already have an assignment - automatic (OrderEventConsumer, triggered at order
  // creation) or manual - by the time an admin opens this dialog for it. Creating a second one is
  // always rejected server-side, but checking here lets the dialog show what already happened
  // instead of the admin hitting that error after filling out the whole form (see section 25).
  const { data: existingAssignmentData, isFetching: existingAssignmentLoading } = useQuery({
    queryKey: ['delivery', 'by-order', order?.id],
    queryFn: () => deliveryService.getByOrder(order!.id),
    enabled: open && Boolean(order),
  })
  const existingAssignment = existingAssignmentData?.data.data?.[0]

  useEffect(() => {
    if (!open) return
    setServerError(''); setOrderError(''); setPartnerError(''); setRouteError('')
    setOrder(presetOrder ?? null)
    setOrderInput('')
    setPartner(null)
    setRoute(null)
    setOverridingRoute(false)
  }, [open, presetOrder])

  // Switching to a different order invalidates any route picked for the previous one.
  useEffect(() => {
    setRoute(null)
    setOverridingRoute(false)
  }, [order?.id])

  const handleSubmit = async () => {
    setServerError(''); setOrderError(''); setPartnerError(''); setRouteError('')
    let hasError = false
    if (!order) { setOrderError('Select an order'); hasError = true }
    if (!partner) { setPartnerError('Select a delivery partner'); hasError = true }
    if (showManualRoutePicker && !route) { setRouteError('Select a route'); hasError = true }
    if (hasError) return

    setSubmitting(true)
    try {
      // Omit routeId entirely when using the order's own automatically-selected route - the
      // backend resolves it from Order.deliveryRouteId in that case, never from this request.
      await deliveryService.assign({
        orderId: order!.id, deliveryPartnerId: partner!.id,
        ...(showManualRoutePicker ? { routeId: route!.id } : {}),
      })
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

        {order && existingAssignmentLoading ? (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={16} /><Typography variant="body2" color="text.secondary">Checking for an existing assignment…</Typography>
          </Box>
        ) : existingAssignment ? (
          // Never let an admin build a second assignment for an order that already has one -
          // POST /delivery/assignments rejects it anyway, but surfacing the real, already-assigned
          // state here (rather than a generic form + error after submit) is what section 25 asks for.
          <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1.5, p: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
              <CheckCircle fontSize="small" color="success" />
              <Typography variant="subtitle2" fontWeight={700}>This order is already assigned</Typography>
              <Chip
                size="small"
                icon={existingAssignment.autoAssigned ? <SmartToy sx={{ fontSize: 14 }} /> : <Person sx={{ fontSize: 14 }} />}
                label={existingAssignment.autoAssigned ? 'Automatic' : 'Manual'}
                sx={{ ml: 'auto' }}
              />
            </Box>
            <Divider sx={{ mb: 1.5 }} />
            <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Delivery Partner</Typography>
                <Typography variant="body2" fontWeight={600}>{existingAssignment.deliveryPartnerName}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Route</Typography>
                <Typography variant="body2" fontWeight={600}>{existingAssignment.routeName} ({existingAssignment.routeCode})</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Status</Typography>
                <Typography variant="body2" fontWeight={600}>{existingAssignment.status}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Partner's Active Deliveries</Typography>
                <Typography variant="body2" fontWeight={600}>{existingAssignment.partnerActiveDeliveries}</Typography>
              </Box>
            </Box>
          </Box>
        ) : (
          <>
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
                        {p.activeDeliveries != null ? ` • ${p.activeDeliveries} active` : ''}
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

            {/* Route: the customer never picks this - it was already determined automatically from
                their delivery address at order-creation time (see Order.deliveryRouteId). This is a
                read-only display of that result, not a selector, unless the admin deliberately
                chooses to override it below. */}
            {!order ? null : autoRouteLoading ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CircularProgress size={16} /><Typography variant="body2" color="text.secondary">Resolving route…</Typography>
              </Box>
            ) : showManualRoutePicker ? (
              noActiveRoutes ? (
                <Alert severity="warning">
                  No active delivery routes available.
                  {canManageRoutes && (
                    <>
                      {' '}
                      <MuiLink component="button" type="button" onClick={() => { onClose(); navigate('/delivery/routes') }}>
                        Manage Routes
                      </MuiLink>
                    </>
                  )}
                </Alert>
              ) : (
                <>
                  {!hasAutoRoute && (
                    <Alert severity="info" sx={{ mb: -1 }}>
                      {order.deliveryRouteId
                        ? "This order's automatically-selected route is no longer active or was removed."
                        : 'No route was automatically selected for this order (an older order, or no route covers its delivery address). Choose one to proceed.'}
                    </Alert>
                  )}
                  <Autocomplete
                    value={route}
                    onChange={(_e, v) => setRoute(v)}
                    options={routes}
                    loading={routesLoading}
                    getOptionLabel={(r) => `${r.routeName} (${r.routeCode})`}
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    renderOption={(props, r) => (
                      <Box component="li" {...props} key={r.id}>
                        <Box>
                          <Typography variant="body2">{r.routeName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {r.routeCode} • {r.area}, {r.city}
                          </Typography>
                        </Box>
                      </Box>
                    )}
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
                  {hasAutoRoute && (
                    <Button size="small" onClick={() => { setOverridingRoute(false); setRoute(null) }} sx={{ alignSelf: 'flex-start' }}>
                      Use automatically-selected route instead
                    </Button>
                  )}
                </>
              )
            ) : (
              <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1.5, p: 1.5 }}>
                <Typography variant="caption" color="text.secondary" display="block">Automatically Selected Route</Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
                  <AltRoute fontSize="small" color="action" />
                  <Typography variant="body2" fontWeight={600}>
                    {autoRoute ? `${autoRoute.routeName} (${autoRoute.routeCode})` : 'Route not assigned'}
                  </Typography>
                </Box>
                {canManageRoutes && (
                  <Button size="small" onClick={() => setOverridingRoute(true)} sx={{ mt: 0.5 }}>
                    Change Route
                  </Button>
                )}
              </Box>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>
          {existingAssignment ? 'Close' : 'Cancel'}
        </Button>
        {!existingAssignment && (
          <Button onClick={handleSubmit} variant="contained" disabled={submitting || (showManualRoutePicker && noActiveRoutes)}>
            {submitting ? <CircularProgress size={20} color="inherit" /> : 'Assign'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
