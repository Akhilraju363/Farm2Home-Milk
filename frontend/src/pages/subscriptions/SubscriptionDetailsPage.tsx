import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Divider, IconButton, Alert, List, ListItem, ListItemText,
} from '@mui/material'
import {
  ArrowBack, Edit, PauseCircle, PlayCircle, Cancel as CancelIcon, ShoppingCart,
  AddCircleOutline, EditCalendar, Person,
} from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { SubscriptionFormDialog } from '../../components/subscription/SubscriptionFormDialog'
import { PauseSubscriptionDialog } from '../../components/subscription/PauseSubscriptionDialog'
import { useAuth } from '../../hooks/useAuth'
import { subscriptionService } from '../../services/subscriptionService'
import { customerService } from '../../services/customerService'
import { orderService } from '../../services/orderService'
import { formatCurrency, formatDate, formatDateTime, statusColor } from '../../utils/formatters'
import { MILK_TYPE_LABELS, SCHEDULE_TYPE_LABELS, SUBSCRIPTION_STATUS_LABELS } from '../../types/subscription.types'

function Section({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
        {action}
      </Box>
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

export function SubscriptionDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const canManage = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [editOpen, setEditOpen] = useState(false)
  const [pauseOpen, setPauseOpen] = useState(false)
  const [resumeOpen, setResumeOpen] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['subscriptions', id],
    queryFn: () => subscriptionService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const subscription = data?.data.data

  // Single lookup, not per-row - safe to enrich the customer's name/mobile here even though the
  // list table (see SubscriptionsPage) only shows the raw customerId to avoid N+1.
  const { data: customerRes } = useQuery({
    queryKey: ['customers', subscription?.customerId],
    queryFn: () => customerService.getById(subscription!.customerId),
    enabled: Boolean(subscription?.customerId),
    retry: false,
  })
  const customer = customerRes?.data.data

  const { data: ordersRes, isLoading: ordersLoading } = useQuery({
    queryKey: ['orders', 'by-subscription', id],
    queryFn: () => orderService.getBySubscription(id!, 10),
    enabled: Boolean(id) && Boolean(subscription),
  })
  const orders = ordersRes?.data.data.content ?? []

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['subscriptions', id] })

  const resumeMutation = useMutation({
    mutationFn: () => subscriptionService.resume(id!),
    onSuccess: () => { enqueueSnackbar('Subscription resumed successfully', { variant: 'success' }); invalidate(); setResumeOpen(false) },
    onError: (err: any) => { enqueueSnackbar(err.response?.data?.message ?? 'Could not resume the subscription.', { variant: 'error' }); setResumeOpen(false) },
  })

  const cancelMutation = useMutation({
    mutationFn: () => subscriptionService.cancel(id!),
    onSuccess: () => { enqueueSnackbar('Subscription cancelled successfully', { variant: 'success' }); invalidate(); setCancelOpen(false) },
    onError: (err: any) => { enqueueSnackbar(err.response?.data?.message ?? 'Could not cancel the subscription.', { variant: 'error' }); setCancelOpen(false) },
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

  if (isError || !subscription) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/subscriptions')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This subscription doesn't exist or you don't have access to it." : "Couldn't load this subscription. Please try again."}
        </Alert>
      </Box>
    )
  }

  const canEdit = subscription.status !== 'CANCELLED' && subscription.status !== 'EXPIRED'

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/subscriptions')}><ArrowBack /></IconButton>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>{MILK_TYPE_LABELS[subscription.milkType]} Subscription</Typography>
              <Chip label={SUBSCRIPTION_STATUS_LABELS[subscription.status]} size="small" color={statusColor(subscription.status)} />
            </Box>
            <Typography variant="body2" color="text.secondary">{subscription.quantity} L • {SCHEDULE_TYPE_LABELS[subscription.scheduleType]}</Typography>
          </Box>
        </Box>
        {canEdit && (
          <Box sx={{ display: 'flex', gap: 1 }}>
            {subscription.status === 'ACTIVE' && (
              <Button variant="outlined" color="warning" startIcon={<PauseCircle />} onClick={() => setPauseOpen(true)}>Pause</Button>
            )}
            {subscription.status === 'PAUSED' && (
              <Button variant="outlined" color="success" startIcon={<PlayCircle />} onClick={() => setResumeOpen(true)}>Resume</Button>
            )}
            <Button variant="outlined" color="error" startIcon={<CancelIcon />} onClick={() => setCancelOpen(true)}>Cancel</Button>
            <Button variant="contained" startIcon={<Edit />} onClick={() => setEditOpen(true)}>Edit</Button>
          </Box>
        )}
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Subscription Overview">
            <Field label="Milk Type" value={MILK_TYPE_LABELS[subscription.milkType]} />
            <Field label="Quantity" value={`${subscription.quantity} L`} />
            <Field label="Status" value={SUBSCRIPTION_STATUS_LABELS[subscription.status]} />
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
          <Section title="Delivery Schedule">
            <Field label="Frequency" value={SCHEDULE_TYPE_LABELS[subscription.scheduleType]} />
            {subscription.scheduleType === 'WEEKLY' && (
              <Field label="Delivery Days" value={(subscription.deliveryDays ?? []).join(', ') || 'Not set'} />
            )}
            <Field label="Start Date" value={formatDate(subscription.startDate)} />
            <Field label="End Date" value={subscription.endDate ? formatDate(subscription.endDate) : 'No end date'} />
            {subscription.status === 'PAUSED' && (
              <Field label="Paused Until" value={subscription.pauseEnd ? formatDate(subscription.pauseEnd) : '—'} />
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Activity">
            <Box sx={{ display: 'flex', gap: 1.5, mb: 1.5 }}>
              <AddCircleOutline fontSize="small" color="action" sx={{ mt: 0.25 }} />
              <Box>
                <Typography variant="body2">Subscription created</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(subscription.createdAt)}</Typography>
              </Box>
            </Box>
            {subscription.pauseStart && (
              <Box sx={{ display: 'flex', gap: 1.5, mb: 1.5 }}>
                <PauseCircle fontSize="small" color="action" sx={{ mt: 0.25 }} />
                <Box>
                  <Typography variant="body2">Paused</Typography>
                  <Typography variant="caption" color="text.secondary">Since {formatDate(subscription.pauseStart)}</Typography>
                </Box>
              </Box>
            )}
            {subscription.updatedAt && subscription.updatedAt !== subscription.createdAt && (
              <Box sx={{ display: 'flex', gap: 1.5 }}>
                <EditCalendar fontSize="small" color="action" sx={{ mt: 0.25 }} />
                <Box>
                  <Typography variant="body2">Last updated</Typography>
                  <Typography variant="caption" color="text.secondary">{formatDateTime(subscription.updatedAt)}</Typography>
                </Box>
              </Box>
            )}
          </Section>
        </Grid>

        <Grid item xs={12}>
          <Section title="Orders">
            {ordersLoading ? (
              <Skeleton variant="rectangular" height={80} sx={{ borderRadius: 1 }} />
            ) : orders.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No orders found.</Typography>
            ) : (
              <List disablePadding>
                {orders.map((o) => (
                  <ListItem key={o.id} disableGutters divider>
                    <ShoppingCart fontSize="small" color="action" sx={{ mr: 1 }} />
                    <ListItemText primary={o.orderNumber} secondary={`${formatCurrency(o.totalAmount)} • ${formatDate(o.orderDate)}`} />
                    <Chip label={o.status} size="small" color={statusColor(o.status)} sx={{ fontSize: 11 }} />
                  </ListItem>
                ))}
              </List>
            )}
          </Section>
        </Grid>
      </Grid>

      <SubscriptionFormDialog
        open={editOpen} subscription={subscription} canSelectCustomer={canManage}
        onClose={() => setEditOpen(false)} onSaved={() => { setEditOpen(false); invalidate() }}
      />

      <PauseSubscriptionDialog
        open={pauseOpen} subscriptionId={subscription.id}
        onClose={() => setPauseOpen(false)} onSaved={() => { setPauseOpen(false); invalidate() }}
      />

      <ConfirmDialog
        open={resumeOpen}
        title="Resume Subscription?"
        message="Deliveries will restart according to the original schedule."
        confirmLabel="Resume"
        loading={resumeMutation.isPending}
        onConfirm={() => resumeMutation.mutate()}
        onClose={() => setResumeOpen(false)}
      />

      <ConfirmDialog
        open={cancelOpen}
        title="Cancel Subscription?"
        message="This is permanent - a cancelled subscription cannot be resumed. The customer will need to create a new one."
        confirmLabel="Cancel Subscription"
        destructive
        loading={cancelMutation.isPending}
        onConfirm={() => cancelMutation.mutate()}
        onClose={() => setCancelOpen(false)}
      />
    </Box>
  )
}
