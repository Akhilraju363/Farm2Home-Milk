import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Divider, IconButton, Alert, List,
  ListItem, ListItemText, Stack, CircularProgress,
} from '@mui/material'
import {
  ArrowBack, Cancel as CancelIcon, LocalShipping, CheckCircle, AssignmentTurnedIn, Person, Payment as PaymentIcon,
  Receipt,
} from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { PayNowDialog } from '../../components/payment/PayNowDialog'
import { useAuth } from '../../hooks/useAuth'
import { orderService } from '../../services/orderService'
import { customerService } from '../../services/customerService'
import { deliveryService } from '../../services/deliveryService'
import { paymentService } from '../../services/paymentService'
import { invoiceService } from '../../services/invoiceService'
import { formatCurrency, formatDate, formatDateTime, statusColor } from '../../utils/formatters'
import {
  MILK_TYPE_LABELS, ORDER_STATUS_LABELS, ORDER_STATUS_TRANSITIONS, ORDER_TYPE_LABELS,
} from '../../types/order.types'
import { ASSIGNMENT_STATUS_LABELS } from '../../types/delivery.types'
import { PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '../../types/payment.types'
import type { OrderStatus } from '../../types/order.types'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, height: '100%' }}>
      <Typography variant="subtitle1" fontWeight={700} mb={1}>{title}</Typography>
      <Divider sx={{ mb: 2 }} />
      {children}
    </Paper>
  )
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
      <Typography variant="body2" fontWeight={500}>{value}</Typography>
    </Box>
  )
}

// One button per reachable next status, matching ORDER_STATUS_TRANSITIONS (which mirrors the
// backend's OrderStatus.canTransitionTo() exactly). CANCELLED is handled separately via its own
// destructive Cancel button/confirm dialog below, not as a plain status-transition button.
const NEXT_STATUS_META: Record<string, { label: string; icon: React.ReactNode }> = {
  ASSIGNED: { label: 'Mark Assigned', icon: <AssignmentTurnedIn /> },
  OUT_FOR_DELIVERY: { label: 'Mark Out for Delivery', icon: <LocalShipping /> },
  DELIVERED: { label: 'Mark Delivered', icon: <CheckCircle /> },
}

export function OrderDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const canManage = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER' || r === 'DELIVERY_MANAGER') ?? false

  const [cancelOpen, setCancelOpen] = useState(false)
  const [nextStatus, setNextStatus] = useState<OrderStatus | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['orders', id],
    queryFn: () => orderService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const order = data?.data.data

  // Single lookup, not per-row - safe to enrich the customer's name/mobile here even though the
  // list table (see OrdersPage) only shows the raw customerId to avoid N+1.
  const { data: customerRes } = useQuery({
    queryKey: ['customers', order?.customerId],
    queryFn: () => customerService.getById(order!.customerId),
    enabled: Boolean(order?.customerId),
    retry: false,
  })
  const customer = customerRes?.data.data

  // GET /delivery/assignments/order/{orderId} is ownership-scoped: admin sees any order's
  // assignment, a DELIVERY_PARTNER only their own, a CUSTOMER only their own order's assignment
  // (extended for real-time tracking - see deliveryService.getByOrder). Safe to fetch for any
  // viewer who can already see this order; a DELIVERY_PARTNER/other viewer without a real
  // relationship to it just gets an empty list.
  const { data: deliveryRes } = useQuery({
    queryKey: ['delivery', 'by-order', id],
    queryFn: () => deliveryService.getByOrder(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const assignment = deliveryRes?.data.data?.[0]

  // GET /payments/order/{orderId} - every payment attempt for this order (an order can have more
  // than one if an earlier attempt failed). Self-scoped server-side (admin sees all, owner sees
  // their own), so this is safe to fetch for any viewer who can already see this order.
  const { data: paymentsRes, isLoading: paymentsLoading } = useQuery({
    queryKey: ['payments', 'by-order', id],
    queryFn: () => paymentService.getByOrder(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const payments = paymentsRes?.data.data ?? []
  const hasPayableProgress = payments.some((p) => p.paymentStatus === 'SUCCESS' || p.paymentStatus === 'PENDING')

  // GET /invoices/order/{orderId} - 404 means no invoice has been generated for this order yet,
  // not an error (see invoiceService.getByOrder). Fetched for any viewer who can already see this
  // order - ownership is enforced server-side the same way as GET /invoices/{id}.
  const { data: invoiceRes, isLoading: invoiceLoading } = useQuery({
    queryKey: ['invoices', 'by-order', id],
    queryFn: () => invoiceService.getByOrder(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const invoice = invoiceRes?.data.data

  const [payNowOpen, setPayNowOpen] = useState(false)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['orders', id] })
  const invalidatePayments = () => queryClient.invalidateQueries({ queryKey: ['payments', 'by-order', id] })
  const invalidateInvoice = () => queryClient.invalidateQueries({ queryKey: ['invoices', 'by-order', id] })

  const statusMutation = useMutation({
    mutationFn: (status: OrderStatus) => orderService.updateStatus(id!, { status }),
    onSuccess: () => { enqueueSnackbar('Order status updated successfully', { variant: 'success' }); invalidate(); setNextStatus(null) },
    onError: (err: any) => { enqueueSnackbar(err.response?.data?.message ?? 'Could not update the order status.', { variant: 'error' }); setNextStatus(null) },
  })

  const cancelMutation = useMutation({
    mutationFn: () => orderService.cancel(id!),
    onSuccess: () => { enqueueSnackbar('Order cancelled successfully', { variant: 'success' }); invalidate(); setCancelOpen(false) },
    onError: (err: any) => { enqueueSnackbar(err.response?.data?.message ?? 'Could not cancel the order.', { variant: 'error' }); setCancelOpen(false) },
  })

  // Invoice generation is manual/admin-triggered from this exact page - there is no automatic
  // trigger anywhere in the backend (no Kafka event, no payment-success hook), and no standalone
  // "+ Create Invoice" button exists on the Invoices list either, since that would imply picking
  // an order out of a list rather than generating from the order you're already looking at.
  const generateInvoiceMutation = useMutation({
    mutationFn: () => invoiceService.generate(id!),
    onSuccess: (res) => {
      enqueueSnackbar('Invoice generated successfully', { variant: 'success' })
      invalidateInvoice()
      navigate(`/invoices/${res.data.data.id}`)
    },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? 'Could not generate the invoice.', { variant: 'error' }),
  })

  if (isLoading) {
    return (
      <Box>
        <Skeleton width={160} height={40} sx={{ mb: 2 }} />
        <Grid container spacing={2}>
          {Array.from({ length: 4 }).map((_, i) => (
            <Grid item xs={12} md={6} key={i}><Skeleton variant="rectangular" height={180} sx={{ borderRadius: 2 }} /></Grid>
          ))}
        </Grid>
      </Box>
    )
  }

  if (isError || !order) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/orders')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This order doesn't exist or you don't have access to it." : "Couldn't load this order. Please try again."}
        </Alert>
      </Box>
    )
  }

  const nextStatuses = ORDER_STATUS_TRANSITIONS[order.status].filter((s) => s !== 'CANCELLED')
  const canCancel = ORDER_STATUS_TRANSITIONS[order.status].includes('CANCELLED')

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/orders')}><ArrowBack /></IconButton>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>{order.orderNumber}</Typography>
              <Chip label={ORDER_STATUS_LABELS[order.status]} size="small" color={statusColor(order.status)} />
            </Box>
            <Typography variant="body2" color="text.secondary">
              {ORDER_TYPE_LABELS[order.orderType]} • {formatCurrency(order.totalAmount)}
            </Typography>
          </Box>
        </Box>
        {(canManage || canCancel || Boolean(invoice) || Boolean(assignment) || (!canManage && !hasPayableProgress && order.status !== 'CANCELLED')) && (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {/* Payment creation belongs to the order's own owner, same as Cancel below - not
                shown to admin/staff viewers, who aren't the ones paying. */}
            {!canManage && !hasPayableProgress && order.status !== 'CANCELLED' && (
              <Button variant="contained" startIcon={<PaymentIcon />} onClick={() => setPayNowOpen(true)}>
                Pay Now
              </Button>
            )}
            {/* Customer-facing entry point to /orders/:id/tracking - admin has its own dedicated
                /delivery/tracking view instead (see Sidebar), so this button is customer-only.
                Shown once any assignment exists, regardless of status - the tracking page itself
                handles "not out for delivery yet" / "tracking has ended" states truthfully. */}
            {!canManage && assignment && (
              <Button variant="outlined" startIcon={<LocalShipping />} onClick={() => navigate(`/orders/${id}/tracking`)}>
                Track Delivery
              </Button>
            )}
            {/* View Invoice is shown to whoever can already see this order (customer/owner or
                admin) once one has been generated - invoice-service's own ownership scoping is
                the real gate. Generate Invoice is FARM_MANAGER/SUPER_ADMIN only (matches
                invoice-service's @PreAuthorize on POST /generate/{orderId}) and not gated on
                order/payment status, since generation is manual/admin-triggered rather than tied
                to a workflow stage. */}
            {!invoiceLoading && (
              invoice ? (
                <Button variant="outlined" startIcon={<Receipt />} onClick={() => navigate(`/invoices/${invoice.id}`)}>
                  View Invoice
                </Button>
              ) : canManage ? (
                <Button
                  variant="outlined" startIcon={generateInvoiceMutation.isPending ? <CircularProgress size={16} /> : <Receipt />}
                  disabled={generateInvoiceMutation.isPending}
                  onClick={() => generateInvoiceMutation.mutate()}
                >
                  Generate Invoice
                </Button>
              ) : null
            )}
            {canManage && nextStatuses.map((s) => (
              <Button
                key={s} variant="outlined" startIcon={NEXT_STATUS_META[s]?.icon}
                onClick={() => setNextStatus(s)}
              >
                {NEXT_STATUS_META[s]?.label ?? s}
              </Button>
            ))}
            {/* Cancel is self-service for the order's own owner too (mirrors Subscription's
                pause/cancel pattern) - status transitions above stay admin/delivery-staff only,
                even though the backend's PATCH /{id}/status endpoint doesn't itself block a
                CUSTOMER from calling it on their own order. */}
            {canCancel && (
              <Button variant="outlined" color="error" startIcon={<CancelIcon />} onClick={() => setCancelOpen(true)}>
                Cancel Order
              </Button>
            )}
          </Stack>
        )}
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Order Overview">
            <Field label="Order Number" value={order.orderNumber} />
            <Field label="Type" value={ORDER_TYPE_LABELS[order.orderType]} />
            <Field label="Order Date" value={formatDate(order.orderDate)} />
            <Field label="Status" value={ORDER_STATUS_LABELS[order.status]} />
            {order.notes && <Field label="Notes" value={order.notes} />}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Customer">
            {customer ? (
              <>
                <Field
                  label="Name"
                  value={
                    canManage
                      ? <RouterLink to={`/customers/${customer.id}`} style={{ color: 'inherit' }}>{customer.firstName} {customer.lastName}</RouterLink>
                      : `${customer.firstName} ${customer.lastName}`
                  }
                />
                <Field label="Mobile" value={customer.mobile} />
                <Field label="Customer ID" value={customer.customerCode} />
              </>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
                <Person fontSize="small" />
                <Typography variant="body2">Customer details unavailable</Typography>
              </Box>
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Items">
            <List disablePadding>
              {order.items.map((item) => (
                <ListItem key={item.id} disableGutters divider>
                  <ListItemText
                    primary={MILK_TYPE_LABELS[item.milkType]}
                    secondary={`${item.quantity} L × ${formatCurrency(item.unitPrice)}`}
                  />
                  <Typography variant="body2" fontWeight={600}>{formatCurrency(item.totalPrice)}</Typography>
                </ListItem>
              ))}
            </List>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2, pt: 1, borderTop: '1px solid', borderColor: 'divider' }}>
              <Typography variant="subtitle2" fontWeight={700}>Total</Typography>
              <Typography variant="subtitle2" fontWeight={700}>{formatCurrency(order.totalAmount)}</Typography>
            </Box>
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Activity">
            <Field label="Created" value={formatDateTime(order.createdAt)} />
            {order.updatedAt && order.updatedAt !== order.createdAt && (
              <Field label="Last Updated" value={formatDateTime(order.updatedAt)} />
            )}
            {order.subscriptionId && (
              <Field
                label="Generated From Subscription"
                value={<RouterLink to={`/subscriptions/${order.subscriptionId}`} style={{ color: 'inherit' }}>View subscription</RouterLink>}
              />
            )}
          </Section>
        </Grid>

        {canManage && (
          <Grid item xs={12} md={6}>
            <Section title="Delivery">
              {assignment ? (
                <>
                  <Field
                    label="Assignment"
                    value={<RouterLink to={`/delivery/${assignment.id}`} style={{ color: 'inherit' }}>{assignment.routeCode} — {assignment.deliveryPartnerName}</RouterLink>}
                  />
                  <Field label="Status" value={ASSIGNMENT_STATUS_LABELS[assignment.status]} />
                  <Field label="Assigned At" value={formatDateTime(assignment.assignedAt)} />
                </>
              ) : (
                <Typography variant="body2" color="text.secondary">No delivery assignment yet.</Typography>
              )}
            </Section>
          </Grid>
        )}

        <Grid item xs={12} md={canManage ? 12 : 6}>
          <Section title="Payments">
            {paymentsLoading ? (
              <Typography variant="body2" color="text.secondary">Loading…</Typography>
            ) : payments.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No payment attempts yet.</Typography>
            ) : (
              <List disablePadding>
                {payments.map((p) => (
                  <ListItem
                    key={p.id} disableGutters divider sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/payments/${p.id}`)}
                  >
                    <PaymentIcon fontSize="small" color="action" sx={{ mr: 1.5 }} />
                    <ListItemText
                      primary={p.paymentReference}
                      secondary={`${PAYMENT_METHOD_LABELS[p.paymentMethod]} • ${formatDateTime(p.createdAt)}`}
                    />
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Typography variant="body2" fontWeight={600}>{formatCurrency(p.amount)}</Typography>
                      <Chip label={PAYMENT_STATUS_LABELS[p.paymentStatus]} size="small" color={statusColor(p.paymentStatus)} sx={{ fontSize: 11 }} />
                    </Box>
                  </ListItem>
                ))}
              </List>
            )}
          </Section>
        </Grid>
      </Grid>

      <PayNowDialog
        open={payNowOpen} orderId={order.id} amount={order.totalAmount}
        onClose={() => setPayNowOpen(false)}
        onPaid={() => { invalidatePayments(); invalidate() }}
      />

      <ConfirmDialog
        open={Boolean(nextStatus)}
        title={`${nextStatus ? NEXT_STATUS_META[nextStatus]?.label ?? nextStatus : ''}?`}
        message={`This will transition the order from ${ORDER_STATUS_LABELS[order.status]} to ${nextStatus ? ORDER_STATUS_LABELS[nextStatus] : ''}.`}
        confirmLabel="Confirm"
        loading={statusMutation.isPending}
        onConfirm={() => nextStatus && statusMutation.mutate(nextStatus)}
        onClose={() => setNextStatus(null)}
      />

      <ConfirmDialog
        open={cancelOpen}
        title="Cancel Order?"
        message="This is permanent - a cancelled order cannot be reopened. The order and its history are kept for records."
        confirmLabel="Cancel Order"
        destructive
        loading={cancelMutation.isPending}
        onConfirm={() => cancelMutation.mutate()}
        onClose={() => setCancelOpen(false)}
      />
    </Box>
  )
}
