import { Box, Typography, Card, Chip, Button, Stack, Tabs, Tab, Paper, Alert, Skeleton } from '@mui/material'
import { LocalShipping, CheckCircle, Cancel as CancelIcon, Schedule } from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { StatusTransitionDialog } from '../../components/delivery/StatusTransitionDialog'
import { DelayDialog } from '../../components/delivery/DelayDialog'
import { LocationSharingControl } from '../../components/delivery/LocationSharingControl'
import { deliveryService } from '../../services/deliveryService'
import { formatDateTime, statusColor } from '../../utils/formatters'
import { ASSIGNMENT_STATUS_LABELS, ASSIGNMENT_STATUS_TRANSITIONS } from '../../types/delivery.types'
import type { Assignment, AssignmentStatus } from '../../types/delivery.types'

const TABS: Array<{ value: AssignmentStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { value: 'DELIVERED', label: 'Completed' },
  { value: 'FAILED', label: 'Failed' },
]

const PRIMARY_ACTION: Partial<Record<AssignmentStatus, { label: string; icon: React.ReactNode; next: AssignmentStatus }>> = {
  ASSIGNED: { label: 'Start Delivery', icon: <LocalShipping fontSize="small" />, next: 'OUT_FOR_DELIVERY' },
  OUT_FOR_DELIVERY: { label: 'Complete Delivery', icon: <CheckCircle fontSize="small" />, next: 'DELIVERED' },
}

function DeliveryCard({ assignment, onAct, onDelay }: {
  assignment: Assignment
  onAct: (a: Assignment, target: AssignmentStatus) => void
  onDelay: (a: Assignment) => void
}) {
  const navigate = useNavigate()
  const action = PRIMARY_ACTION[assignment.status]
  const canFail = ASSIGNMENT_STATUS_TRANSITIONS[assignment.status].includes('FAILED')

  return (
    <Card variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
        <Box sx={{ cursor: 'pointer' }} onClick={() => navigate(`/delivery/${assignment.id}`)}>
          <Typography variant="subtitle2" fontWeight={700} sx={{ fontFamily: 'monospace' }}>
            Order {assignment.orderId.slice(0, 8)}…
          </Typography>
          <Typography variant="caption" color="text.secondary">Route {assignment.routeCode} • {formatDateTime(assignment.assignedAt)}</Typography>
        </Box>
        <Chip label={ASSIGNMENT_STATUS_LABELS[assignment.status]} size="small" color={statusColor(assignment.status)} />
      </Box>

      {(action || canFail) && (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1.5 }}>
          {action && (
            <Button size="small" variant="contained" startIcon={action.icon} onClick={() => onAct(assignment, action.next)}>
              {action.label}
            </Button>
          )}
          {assignment.status !== 'DELIVERED' && assignment.status !== 'FAILED' && (
            <Button size="small" variant="outlined" color="warning" startIcon={<Schedule fontSize="small" />} onClick={() => onDelay(assignment)}>
              Delay
            </Button>
          )}
          {canFail && (
            <Button size="small" variant="outlined" color="error" startIcon={<CancelIcon fontSize="small" />} onClick={() => onAct(assignment, 'FAILED')}>
              Failed
            </Button>
          )}
        </Stack>
      )}

      {/* Location submission is only accepted by the backend while OUT_FOR_DELIVERY - tracking
          hasn't started yet at ASSIGNED, and has ended at DELIVERED/FAILED, so the control is only
          rendered here, not hidden-but-disabled elsewhere. */}
      {assignment.status === 'OUT_FOR_DELIVERY' && <LocationSharingControl assignmentId={assignment.id} />}
    </Card>
  )
}

// Order/customer details (items, address) aren't available to a DELIVERY_PARTNER caller for
// their own assignment - order-service and customer-service only recognize "the customer" or
// admin as an order's owner, no ownership path exists yet for "assigned delivery partner". Cards
// here show what's actually returned by the assignment itself (order id, route, status) - see the
// final report for this as a documented backend blocker, not something faked here.
export function MyDeliveriesPage() {
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()

  const [tab, setTab] = useState<AssignmentStatus | ''>('')
  const [actionTarget, setActionTarget] = useState<{ assignment: Assignment; status: AssignmentStatus } | null>(null)
  const [delayTarget, setDelayTarget] = useState<Assignment | null>(null)
  const [statusError, setStatusError] = useState('')
  const [delayError, setDelayError] = useState('')

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['delivery', 'my-deliveries', tab],
    queryFn: () => deliveryService.search({ status: (tab || undefined) as AssignmentStatus | undefined, size: 50 }),
  })
  const assignments = data?.data.data.content ?? []

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['delivery'] })

  const statusMutation = useMutation({
    mutationFn: (extra: { deliveryProof?: string; failureReason?: string }) =>
      deliveryService.updateStatus(actionTarget!.assignment.id, { status: actionTarget!.status, ...extra }),
    onSuccess: () => { enqueueSnackbar(`Marked as ${ASSIGNMENT_STATUS_LABELS[actionTarget!.status]}`, { variant: 'success' }); invalidate(); setActionTarget(null); setStatusError('') },
    onError: (err: any) => setStatusError(err.response?.data?.message ?? 'Could not update the delivery status.'),
  })

  const delayMutation = useMutation({
    mutationFn: (reason: string) => deliveryService.markDelayed(delayTarget!.id, { reason }),
    onSuccess: () => { enqueueSnackbar('Delay notification sent', { variant: 'success' }); setDelayTarget(null); setDelayError('') },
    onError: (err: any) => setDelayError(err.response?.data?.message ?? 'Could not send the delay notification.'),
  })

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={0.5}>My Deliveries</Typography>
      <Typography variant="body2" color="text.secondary" mb={3}>Deliveries assigned to you.</Typography>

      <Tabs value={tab} onChange={(_e, v) => setTab(v)} variant="scrollable" scrollButtons="auto" sx={{ mb: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
        {TABS.map((t) => <Tab key={t.value} value={t.value} label={t.label} />)}
      </Tabs>

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Couldn't load your deliveries. Please check your connection and try again.
        </Alert>
      ) : isLoading ? (
        <Stack spacing={1.5}>
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} variant="rectangular" height={90} sx={{ borderRadius: 2 }} />)}
        </Stack>
      ) : assignments.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 5, textAlign: 'center', borderRadius: 2 }}>
          <LocalShipping sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
          <Typography variant="body1" fontWeight={600}>No deliveries here.</Typography>
        </Paper>
      ) : (
        <Stack spacing={1.5}>
          {assignments.map((a) => (
            <DeliveryCard
              key={a.id} assignment={a}
              onAct={(assignment, status) => setActionTarget({ assignment, status })}
              onDelay={(assignment) => setDelayTarget(assignment)}
            />
          ))}
        </Stack>
      )}

      <StatusTransitionDialog
        open={Boolean(actionTarget)} targetStatus={actionTarget?.status ?? null}
        loading={statusMutation.isPending} serverError={statusError}
        onClose={() => { setActionTarget(null); setStatusError('') }}
        onConfirm={(extra) => statusMutation.mutate(extra)}
      />

      <DelayDialog
        open={Boolean(delayTarget)} loading={delayMutation.isPending} serverError={delayError}
        onClose={() => { setDelayTarget(null); setDelayError('') }}
        onConfirm={(reason) => delayMutation.mutate(reason)}
      />
    </Box>
  )
}
