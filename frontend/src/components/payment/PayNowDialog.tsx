import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, CircularProgress, Alert, Typography,
} from '@mui/material'
import { Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { paymentService } from '../../services/paymentService'
import { useAuth } from '../../hooks/useAuth'
import { formatCurrency } from '../../utils/formatters'
import { completeOnlinePayment } from '../../utils/onlinePayment'
import type { OnlinePaymentOutcome } from '../../utils/onlinePayment'
import type { Payment } from '../../types/payment.types'
import { PaymentMethodSelector, CUSTOMER_PAYMENT_METHODS } from './PaymentMethodSelector'
import type { CustomerPaymentMethod } from './PaymentMethodSelector'
import { PaymentResultView } from './PaymentResultView'

interface Props {
  open: boolean
  orderId: string
  amount: number
  /** The order already has a PENDING online payment - only resuming it is possible. */
  resumeOnline?: boolean
  onClose: () => void
  onPaid: () => void
}

// Select method -> POST /payments (initiate). CASH stays PENDING until delivery staff record it;
// WALLET resolves synchronously; RAZORPAY opens Razorpay Checkout for the server-created gateway
// order and the outcome is confirmed through POST /payments/{id}/verify and a fresh read of the
// payment (see completeOnlinePayment). Re-initiating an online payment while one is still PENDING
// resumes that same gateway order server-side, so "Try Again" never creates a second charge.
export function PayNowDialog({ open, orderId, amount, resumeOnline = false, onClose, onPaid }: Props) {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  // Once an online payment is open for this order (resumed, dismissed or still processing) the
  // backend only accepts continuing it - a second, different payment would be rejected.
  const [onlineOpen, setOnlineOpen] = useState(false)
  const methods: CustomerPaymentMethod[] = resumeOnline || onlineOpen ? ['RAZORPAY'] : CUSTOMER_PAYMENT_METHODS
  const [method, setMethod] = useState<CustomerPaymentMethod>('RAZORPAY')
  const [result, setResult] = useState<{ payment: Payment; outcome?: OnlinePaymentOutcome; message?: string } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (open) { setMethod('RAZORPAY'); setResult(null); setSubmitting(false); setError(''); setOnlineOpen(false) }
  }, [open])

  const pay = async () => {
    setSubmitting(true)
    setError('')
    try {
      const payment = (await paymentService.initiate({ orderId, amount, paymentMethod: method })).data.data
      if (method === 'RAZORPAY') {
        const online = await completeOnlinePayment(payment, {
          description: `Order payment ${payment.paymentReference}`,
          prefill: { contact: user?.mobile, email: user?.email },
        })
        setResult({ payment: online.payment, outcome: online.outcome, message: online.message })
        setOnlineOpen(online.payment.paymentStatus === 'PENDING')
      } else {
        setResult({ payment })
      }
      queryClient.invalidateQueries({ queryKey: ['payments'] })
      queryClient.invalidateQueries({ queryKey: ['wallet'] })
      onPaid()
    } catch (err: any) {
      setError(err.response?.data?.message ?? err.message ?? 'Could not start the payment. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const canRetry = result?.outcome === 'cancelled' || result?.outcome === 'failed'

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>{resumeOnline ? 'Complete Payment' : 'Pay Now'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {error && <Alert severity="error">{error}</Alert>}

        {result ? (
          <PaymentResultView payment={result.payment} outcome={result.outcome} message={result.message} />
        ) : (
          <>
            <Typography variant="body2" color="text.secondary">Amount to pay</Typography>
            <Typography variant="h5" fontWeight={700} mt={-1.5}>{formatCurrency(amount)}</Typography>
            <PaymentMethodSelector value={method} onChange={setMethod} methods={methods} disabled={submitting} />
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        {result ? (
          <>
            {canRetry && (
              <Button onClick={() => { setResult(null); setMethod('RAZORPAY') }} color="inherit">Try Again</Button>
            )}
            <Button onClick={onClose} variant="contained" fullWidth={!canRetry}>Done</Button>
          </>
        ) : (
          <>
            <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
            <Button onClick={pay} variant="contained" disabled={submitting}>
              {submitting ? <CircularProgress size={20} color="inherit" /> : method === 'RAZORPAY' ? 'Pay Online' : 'Pay Now'}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  )
}
