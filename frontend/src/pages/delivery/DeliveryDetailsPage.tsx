import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Divider, IconButton, Alert, Stack,
} from '@mui/material'
import {
  ArrowBack, LocalShipping, CheckCircle, Cancel as CancelIcon, Schedule, Person,
} from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { StatusTransitionDialog } from '../../components/delivery/StatusTransitionDialog'
import { DelayDialog } from '../../components/delivery/DelayDialog'
import { useAuth } from '../../hooks/useAuth'
import { deliveryService } from '../../services/deliveryService'
import { orderService } from '../../services/orderService'
import { customerService } from '../../services/customerService'
import { formatCurrency, formatDate, formatDateTime, statusColor } from '../../utils/formatters'
import { ASSIGNMENT_STATUS_LABELS, ASSIGNMENT_STATUS_TRANSITIONS } from '../../types/delivery.types'
import { MILK_TYPE_LABELS, ORDER_TYPE_LABELS } from '../../types/order.types'
import type { AssignmentStatus } from '../../types/delivery.types'

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

const NEXT_STATUS_META: Record<string, { label: string; icon: React.ReactNode }> = {
  OUT_FOR_DELIVERY: { label: 'Start Delivery', icon: <LocalShipping /> },
  DELIVERED: { label: 'Complete Delivery', icon: <CheckCircle /> },
}

export function DeliveryDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const canManage = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER' || r === 'DELIVERY_MANAGER') ?? false

  const [nextStatus, setNextStatus] = useState<AssignmentStatus | null>(null)
  const [delayOpen, setDelayOpen] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [delayError, setDelayError] = useState('')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['delivery', id],
    queryFn: () => deliveryService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const assignment = data?.data.data

  // order-service/customer-service only recognize "the customer" or admin as an order's owner -
  // a DELIVERY_PARTNER gets a 404 from both for their own assigned order (confirmed; there is no
  // ownership path for "assigned delivery partner" in either service yet). So this enrichment is
  // only attempted for admin viewers; a partner sees the fields already on the assignment itself
  // (orderId, route, status) and a note that order/customer detail isn't available to them yet.
  const { data: orderRes } = useQuery({
    queryKey: ['orders', assignment?.orderId],
    queryFn: () => orderService.getById(assignment!.orderId),
    enabled: Boolean(assignment?.orderId) && canManage,
    retry: false,
  })
  const order = orderRes?.data.data

  const { data: customerRes } = useQuery({
    queryKey: ['customers', order?.customerId],
    queryFn: () => customerService.getById(order!.customerId),
    enabled: Boolean(order?.customerId) && canManage,
    retry: false,
  })
  const customer = customerRes?.data.data

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['delivery', id] })

  const statusMutation = useMutation({
    mutationFn: (extra: { deliveryProof?: string; failureReason?: string }) =>
      deliveryService.updateStatus(id!, { status: nextStatus!, ...extra }),
    onSuccess: () => { enqueueSnackbar(`Marked as ${ASSIGNMENT_STATUS_LABELS[nextStatus!]}`, { variant: 'success' }); invalidate(); setNextStatus(null); setStatusError('') },
    onError: (err: any) => setStatusError(err.response?.data?.message ?? 'Could not update the delivery status.'),
  })

  const delayMutation = useMutation({
    mutationFn: (reason: string) => deliveryService.markDelayed(id!, { reason }),
    onSuccess: () => { enqueueSnackbar('Delay notification sent', { variant: 'success' }); setDelayOpen(false); setDelayError('') },
    onError: (err: any) => setDelayError(err.response?.data?.message ?? 'Could not send the delay notification.'),
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

  if (isError || !assignment) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/delivery')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This delivery doesn't exist or you don't have access to it." : "Couldn't load this delivery. Please try again."}
        </Alert>
      </Box>
    )
  }

  const nextStatuses = ASSIGNMENT_STATUS_TRANSITIONS[assignment.status]
  const canAct = nextStatuses.length > 0

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/delivery')}><ArrowBack /></IconButton>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>Delivery {assignment.routeCode}</Typography>
              <Chip label={ASSIGNMENT_STATUS_LABELS[assignment.status]} size="small" color={statusColor(assignment.status)} />
            </Box>
            <Typography variant="body2" color="text.secondary">{assignment.deliveryPartnerName}</Typography>
          </Box>
        </Box>
        {canAct && (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {nextStatuses.filter((s) => s !== 'FAILED').map((s) => (
              <Button key={s} variant="outlined" startIcon={NEXT_STATUS_META[s]?.icon} onClick={() => setNextStatus(s)}>
                {NEXT_STATUS_META[s]?.label ?? s}
              </Button>
            ))}
            <Button variant="outlined" color="warning" startIcon={<Schedule />} onClick={() => setDelayOpen(true)}>Notify Delay</Button>
            {nextStatuses.includes('FAILED') && (
              <Button variant="outlined" color="error" startIcon={<CancelIcon />} onClick={() => setNextStatus('FAILED')}>Mark Failed</Button>
            )}
          </Stack>
        )}
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Delivery Overview">
            <Field label="Assignment ID" value={assignment.id} />
            <Field label="Order ID" value={canManage ? <RouterLink to={`/orders/${assignment.orderId}`} style={{ color: 'inherit' }}>{assignment.orderId}</RouterLink> : assignment.orderId} />
            <Field label="Route" value={`${assignment.routeCode}`} />
            <Field label="Status" value={ASSIGNMENT_STATUS_LABELS[assignment.status]} />
            {assignment.failureReason && <Field label="Failure Reason" value={assignment.failureReason} />}
            {assignment.deliveryProof && <Field label="Delivery Proof" value={assignment.deliveryProof} />}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Delivery Partner">
            <Field label="Name" value={assignment.deliveryPartnerName} />
            <Field label="Mobile" value={assignment.deliveryPartnerMobile} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Order">
            {!canManage ? (
              <Box sx={{ color: 'text.secondary' }}>
                <Typography variant="body2">Order details aren't available to this view yet.</Typography>
              </Box>
            ) : order ? (
              <>
                <Field label="Order Number" value={order.orderNumber} />
                <Field label="Type" value={ORDER_TYPE_LABELS[order.orderType]} />
                <Field label="Order Date" value={formatDate(order.orderDate)} />
                <Field label="Items" value={order.items.map((it) => `${MILK_TYPE_LABELS[it.milkType]} (${it.quantity}L)`).join(', ')} />
                <Field label="Total" value={formatCurrency(order.totalAmount)} />
              </>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
                <Typography variant="body2">Order details unavailable</Typography>
              </Box>
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Customer">
            {!canManage ? (
              <Box sx={{ color: 'text.secondary' }}>
                <Typography variant="body2">Customer details aren't available to this view yet.</Typography>
              </Box>
            ) : customer ? (
              <>
                <Field label="Name" value={<RouterLink to={`/customers/${customer.id}`} style={{ color: 'inherit' }}>{customer.firstName} {customer.lastName}</RouterLink>} />
                <Field label="Mobile" value={customer.mobile} />
              </>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
                <Person fontSize="small" />
                <Typography variant="body2">Customer details unavailable</Typography>
              </Box>
            )}
          </Section>
        </Grid>

        <Grid item xs={12}>
          <Section title="Timeline">
            <Box sx={{ display: 'flex', gap: 1.5, mb: 1.5 }}>
              <LocalShipping fontSize="small" color="action" sx={{ mt: 0.25 }} />
              <Box>
                <Typography variant="body2">Assigned</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(assignment.assignedAt)}</Typography>
              </Box>
            </Box>
            {assignment.deliveredAt && (
              <Box sx={{ display: 'flex', gap: 1.5 }}>
                <CheckCircle fontSize="small" color="action" sx={{ mt: 0.25 }} />
                <Box>
                  <Typography variant="body2">Delivered</Typography>
                  <Typography variant="caption" color="text.secondary">{formatDateTime(assignment.deliveredAt)}</Typography>
                </Box>
              </Box>
            )}
          </Section>
        </Grid>
      </Grid>

      <StatusTransitionDialog
        open={Boolean(nextStatus)} targetStatus={nextStatus}
        loading={statusMutation.isPending} serverError={statusError}
        onClose={() => { setNextStatus(null); setStatusError('') }}
        onConfirm={(extra) => statusMutation.mutate(extra)}
      />

      <DelayDialog
        open={delayOpen} loading={delayMutation.isPending} serverError={delayError}
        onClose={() => { setDelayOpen(false); setDelayError('') }}
        onConfirm={(reason) => delayMutation.mutate(reason)}
      />
    </Box>
  )
}
