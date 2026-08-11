import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Divider, IconButton, Alert,
} from '@mui/material'
import { ArrowBack, Undo } from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { RefundDialog } from '../../components/payment/RefundDialog'
import { useAuth } from '../../hooks/useAuth'
import { paymentService } from '../../services/paymentService'
import { formatCurrency, formatDateTime, statusColor } from '../../utils/formatters'
import { PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '../../types/payment.types'

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

// Refund is FARM_MANAGER/SUPER_ADMIN only on the backend - narrower than
// GET /payments/reports's SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER, so this is checked
// separately from useAuth().isAdmin() (which includes DELIVERY_MANAGER).
function useCanRefund() {
  const { user } = useAuth()
  return user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false
}

export function PaymentDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const canRefund = useCanRefund()

  const [refundOpen, setRefundOpen] = useState(false)
  const [refundError, setRefundError] = useState('')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['payments', id],
    queryFn: () => paymentService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const payment = data?.data.data

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['payments', id] })

  const refundMutation = useMutation({
    mutationFn: () => paymentService.refund(id!),
    onSuccess: () => {
      enqueueSnackbar('Payment refunded successfully', { variant: 'success' })
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['wallet'] })
      setRefundOpen(false)
      setRefundError('')
    },
    onError: (err: any) => setRefundError(err.response?.data?.message ?? 'Could not refund this payment.'),
  })

  if (isLoading) {
    return (
      <Box>
        <Skeleton width={160} height={40} sx={{ mb: 2 }} />
        <Grid container spacing={2}>
          {Array.from({ length: 3 }).map((_, i) => (
            <Grid item xs={12} md={6} key={i}><Skeleton variant="rectangular" height={160} sx={{ borderRadius: 2 }} /></Grid>
          ))}
        </Grid>
      </Box>
    )
  }

  if (isError || !payment) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/payments')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This payment doesn't exist or you don't have access to it." : "Couldn't load this payment. Please try again."}
        </Alert>
      </Box>
    )
  }

  const canShowRefund = canRefund && payment.paymentStatus === 'SUCCESS'

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/payments')}><ArrowBack /></IconButton>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>{payment.paymentReference}</Typography>
              <Chip label={PAYMENT_STATUS_LABELS[payment.paymentStatus]} size="small" color={statusColor(payment.paymentStatus)} />
            </Box>
            <Typography variant="body2" color="text.secondary">
              {PAYMENT_METHOD_LABELS[payment.paymentMethod]} • {formatCurrency(payment.amount)}
            </Typography>
          </Box>
        </Box>
        {canShowRefund && (
          <Button variant="outlined" color="error" startIcon={<Undo />} onClick={() => setRefundOpen(true)}>
            Refund Payment
          </Button>
        )}
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Payment Overview">
            <Field label="Payment ID" value={payment.id} />
            <Field label="Payment Reference" value={payment.paymentReference} />
            <Field
              label="Order"
              value={<RouterLink to={`/orders/${payment.orderId}`} style={{ color: 'inherit' }}>{payment.orderId}</RouterLink>}
            />
            <Field label="Amount" value={formatCurrency(payment.amount)} />
            <Field label="Method" value={PAYMENT_METHOD_LABELS[payment.paymentMethod]} />
            <Field label="Status" value={PAYMENT_STATUS_LABELS[payment.paymentStatus]} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Gateway Information">
            {payment.gatewayOrderId || payment.gatewayPaymentId ? (
              <>
                {payment.gatewayOrderId && <Field label="Gateway Order ID" value={payment.gatewayOrderId} />}
                {payment.gatewayPaymentId && <Field label="Gateway Payment ID" value={payment.gatewayPaymentId} />}
              </>
            ) : (
              <Typography variant="body2" color="text.secondary">
                No gateway involved for this payment method.
              </Typography>
            )}
          </Section>
        </Grid>

        <Grid item xs={12}>
          <Section title="Timeline">
            <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
              <Field label="Created" value={formatDateTime(payment.createdAt)} />
              <Field label="Paid At" value={payment.paidAt ? formatDateTime(payment.paidAt) : '—'} />
            </Box>
          </Section>
        </Grid>
      </Grid>

      <RefundDialog
        open={refundOpen} payment={payment}
        loading={refundMutation.isPending} serverError={refundError}
        onClose={() => { setRefundOpen(false); setRefundError('') }}
        onConfirm={() => refundMutation.mutate()}
      />
    </Box>
  )
}
