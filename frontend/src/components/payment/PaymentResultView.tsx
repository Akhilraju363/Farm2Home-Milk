import { Alert, Box, Chip, CircularProgress, Divider, Typography } from '@mui/material'
import { CheckCircle, Cancel as CancelIcon, HourglassTop, DoNotDisturbOn } from '@mui/icons-material'
import { formatCurrency, statusColor } from '../../utils/formatters'
import { PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '../../types/payment.types'
import type { Payment } from '../../types/payment.types'
import type { OnlinePaymentOutcome } from '../../utils/onlinePayment'

interface Props {
  payment: Payment
  /** Set for online payments; derived from the backend status otherwise. */
  outcome?: OnlinePaymentOutcome
  message?: string
}

const HEADLINES: Record<OnlinePaymentOutcome, string> = {
  paid: 'Payment Successful',
  failed: 'Payment Failed',
  processing: 'Payment Processing',
  cancelled: 'Payment Cancelled',
}

/** Shows a payment's result as the backend reports it - never a client-side assumption. */
export function PaymentResultView({ payment, outcome, message }: Props) {
  const resolved: OnlinePaymentOutcome = outcome
    ?? (payment.paymentStatus === 'SUCCESS' ? 'paid' : payment.paymentStatus === 'FAILED' ? 'failed' : 'processing')
  const isCashPending = payment.paymentMethod === 'CASH' && payment.paymentStatus === 'PENDING'

  return (
    <Box sx={{ textAlign: 'center', py: 1 }}>
      {resolved === 'paid' ? (
        <CheckCircle color="success" sx={{ fontSize: 48, mb: 1 }} />
      ) : resolved === 'failed' ? (
        <CancelIcon color="error" sx={{ fontSize: 48, mb: 1 }} />
      ) : resolved === 'cancelled' ? (
        <DoNotDisturbOn color="warning" sx={{ fontSize: 48, mb: 1 }} />
      ) : isCashPending ? (
        <HourglassTop color="warning" sx={{ fontSize: 48, mb: 1 }} />
      ) : (
        <CircularProgress size={40} sx={{ mb: 1 }} />
      )}
      <Typography variant="h6" fontWeight={700}>
        {isCashPending ? 'Cash on Delivery Selected' : HEADLINES[resolved]}
      </Typography>
      <Chip label={PAYMENT_STATUS_LABELS[payment.paymentStatus]} color={statusColor(payment.paymentStatus)} size="small" sx={{ mt: 1, mb: 2 }} />

      {message && (
        <Alert severity={resolved === 'paid' ? 'info' : resolved === 'processing' || resolved === 'cancelled' ? 'warning' : 'error'} sx={{ textAlign: 'left', mb: 1.5 }}>
          {message}
        </Alert>
      )}
      {resolved === 'processing' && !isCashPending && (
        <Alert severity="info" sx={{ textAlign: 'left', mb: 1.5 }}>
          We're waiting for the payment gateway to confirm this payment. Its status will update
          automatically on My Payments - there's no need to pay again.
        </Alert>
      )}
      {resolved === 'cancelled' && (
        <Alert severity="info" sx={{ textAlign: 'left', mb: 1.5 }}>
          You can complete the payment later from the order page.
        </Alert>
      )}

      <Divider sx={{ my: 1.5 }} />
      <Box sx={{ textAlign: 'left' }}>
        <Typography variant="caption" color="text.secondary" display="block">Reference</Typography>
        <Typography variant="body2" fontWeight={600} mb={1}>{payment.paymentReference}</Typography>
        <Typography variant="caption" color="text.secondary" display="block">Amount</Typography>
        <Typography variant="body2" fontWeight={600} mb={1}>{formatCurrency(payment.amount)}</Typography>
        <Typography variant="caption" color="text.secondary" display="block">Method</Typography>
        <Typography variant="body2" fontWeight={600}>{PAYMENT_METHOD_LABELS[payment.paymentMethod]}</Typography>
      </Box>
      {isCashPending && (
        <Alert severity="info" sx={{ mt: 2, textAlign: 'left' }}>
          Pay the delivery partner in cash on delivery.
        </Alert>
      )}
    </Box>
  )
}
