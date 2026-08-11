import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, CircularProgress, Alert,
  RadioGroup, FormControlLabel, Radio, Typography, Chip, Divider,
} from '@mui/material'
import { CheckCircle, Cancel as CancelIcon, Close } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { paymentService } from '../../services/paymentService'
import { formatCurrency, statusColor } from '../../utils/formatters'
import { PAYMENT_METHODS, PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '../../types/payment.types'
import type { Payment, PaymentMethod } from '../../types/payment.types'

interface Props {
  open: boolean
  orderId: string
  amount: number
  onClose: () => void
  onPaid: () => void
}

// Checkout flow: select method -> POST /payments (initiate) -> for UPI/RAZORPAY, POST /{id}/verify.
// A real deployment would load Razorpay's Checkout.js with the initiate response's
// gatewayCheckoutKeyId/gatewayOrderId and use ITS callback's gatewayPaymentId+signature for
// verify(). This environment only has the mock gateway provider active (no real Razorpay
// account/keys), so verify() is auto-submitted using the backend's own documented test signature
// (MockPaymentGatewayProvider.MOCK_VALID_SIGNATURE) - a real, intentional backend testing hook,
// not something invented here. WALLET resolves synchronously (no verify step); CASH stays PENDING
// until delivery staff record it paid via /callback (not a customer-facing action).
export function PayNowDialog({ open, orderId, amount, onClose, onPaid }: Props) {
  const queryClient = useQueryClient()
  const [method, setMethod] = useState<PaymentMethod>('UPI')
  const [result, setResult] = useState<Payment | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (open) { setMethod('UPI'); setResult(null); setVerifying(false); setError('') }
  }, [open])

  const initiateMutation = useMutation({
    mutationFn: () => paymentService.initiate({ orderId, amount, paymentMethod: method }),
    onSuccess: async (res) => {
      const payment = res.data.data
      if ((payment.paymentMethod === 'UPI' || payment.paymentMethod === 'RAZORPAY') && payment.paymentStatus === 'PENDING' && payment.gatewayOrderId) {
        setVerifying(true)
        try {
          const verifyRes = await paymentService.verify(payment.id, {
            gatewayOrderId: payment.gatewayOrderId,
            gatewayPaymentId: `mock_pay_${payment.id.slice(0, 12)}`,
            signature: 'MOCK-VALID-SIGNATURE',
          })
          setResult(verifyRes.data.data)
        } catch (err: any) {
          setError(err.response?.data?.message ?? 'Payment could not be verified. It may still complete shortly - check My Payments.')
          setResult(payment)
        } finally {
          setVerifying(false)
        }
      } else {
        setResult(payment)
      }
      queryClient.invalidateQueries({ queryKey: ['payments'] })
      queryClient.invalidateQueries({ queryKey: ['wallet'] })
      onPaid()
    },
    onError: (err: any) => setError(err.response?.data?.message ?? 'Could not start the payment. Please try again.'),
  })

  const submitting = initiateMutation.isPending || verifying

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Pay Now</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {error && <Alert severity="error">{error}</Alert>}

        {result ? (
          <Box sx={{ textAlign: 'center', py: 2 }}>
            {result.paymentStatus === 'SUCCESS' ? (
              <CheckCircle color="success" sx={{ fontSize: 48, mb: 1 }} />
            ) : result.paymentStatus === 'FAILED' ? (
              <CancelIcon color="error" sx={{ fontSize: 48, mb: 1 }} />
            ) : (
              <CircularProgress size={40} sx={{ mb: 1 }} />
            )}
            <Typography variant="h6" fontWeight={700}>
              {result.paymentStatus === 'SUCCESS' ? 'Payment Successful'
                : result.paymentStatus === 'FAILED' ? 'Payment Failed'
                : 'Payment Pending'}
            </Typography>
            <Chip label={PAYMENT_STATUS_LABELS[result.paymentStatus]} color={statusColor(result.paymentStatus)} size="small" sx={{ mt: 1, mb: 2 }} />
            <Divider sx={{ my: 1.5 }} />
            <Box sx={{ textAlign: 'left' }}>
              <Typography variant="caption" color="text.secondary" display="block">Reference</Typography>
              <Typography variant="body2" fontWeight={600} mb={1}>{result.paymentReference}</Typography>
              <Typography variant="caption" color="text.secondary" display="block">Amount</Typography>
              <Typography variant="body2" fontWeight={600} mb={1}>{formatCurrency(result.amount)}</Typography>
              <Typography variant="caption" color="text.secondary" display="block">Method</Typography>
              <Typography variant="body2" fontWeight={600}>{PAYMENT_METHOD_LABELS[result.paymentMethod]}</Typography>
            </Box>
            {result.paymentMethod === 'CASH' && result.paymentStatus === 'PENDING' && (
              <Alert severity="info" sx={{ mt: 2, textAlign: 'left' }}>
                Pay the delivery partner in cash on delivery.
              </Alert>
            )}
          </Box>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary">Amount to pay</Typography>
            <Typography variant="h5" fontWeight={700}>{formatCurrency(amount)}</Typography>
            <RadioGroup value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              {PAYMENT_METHODS.map((m) => (
                <FormControlLabel key={m} value={m} control={<Radio />} label={PAYMENT_METHOD_LABELS[m]} disabled={submitting} />
              ))}
            </RadioGroup>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        {result ? (
          <Button onClick={onClose} variant="contained" fullWidth>Done</Button>
        ) : (
          <>
            <Button onClick={onClose} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
            <Button onClick={() => initiateMutation.mutate()} variant="contained" disabled={submitting}>
              {submitting ? <CircularProgress size={20} color="inherit" /> : 'Pay Now'}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  )
}
