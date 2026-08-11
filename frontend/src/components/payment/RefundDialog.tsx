import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Alert, Box, Typography, CircularProgress } from '@mui/material'
import { PAYMENT_METHOD_LABELS } from '../../types/payment.types'
import type { Payment } from '../../types/payment.types'
import { formatCurrency } from '../../utils/formatters'

const METHOD_MECHANICS: Record<string, string> = {
  WALLET: 'The full amount will be credited back to the customer’s in-app wallet.',
  UPI: 'The full amount will be refunded through the payment gateway to the original payment source.',
  RAZORPAY: 'The full amount will be refunded through the payment gateway to the original payment source.',
  CASH: 'The payment will be marked Refunded. Any physical cash return happens outside this system.',
}

interface Props {
  open: boolean
  payment: Payment | null
  loading?: boolean
  serverError?: string
  onClose: () => void
  onConfirm: () => void
}

// Refund is always the full amount - there is no partial refund on the backend - and only
// SUCCESS payments are eligible (enforced server-side; this dialog is only ever opened for one).
export function RefundDialog({ open, payment, loading, serverError, onClose, onConfirm }: Props) {
  if (!payment) return null

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Refund Payment?</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {serverError && <Alert severity="error">{serverError}</Alert>}
        <Box>
          <Typography variant="caption" color="text.secondary" display="block">Payment Reference</Typography>
          <Typography variant="body2" fontWeight={600} mb={1.5}>{payment.paymentReference}</Typography>
          <Typography variant="caption" color="text.secondary" display="block">Amount</Typography>
          <Typography variant="body2" fontWeight={600} mb={1.5}>{formatCurrency(payment.amount)}</Typography>
          <Typography variant="caption" color="text.secondary" display="block">Payment Method</Typography>
          <Typography variant="body2" fontWeight={600}>{PAYMENT_METHOD_LABELS[payment.paymentMethod]}</Typography>
        </Box>
        <Alert severity="warning">
          This action will refund the full payment amount. {METHOD_MECHANICS[payment.paymentMethod]}
        </Alert>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={loading} color="inherit">Cancel</Button>
        <Button
          onClick={onConfirm} disabled={loading} variant="contained" color="error"
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Confirm Refund
        </Button>
      </DialogActions>
    </Dialog>
  )
}
