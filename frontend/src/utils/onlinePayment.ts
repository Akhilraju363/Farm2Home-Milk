import { paymentService } from '../services/paymentService'
import type { Payment } from '../types/payment.types'
import { isRazorpayKey, openRazorpayCheckout } from './razorpayCheckout'

// payment-service's MockPaymentGatewayProvider.MOCK_VALID_SIGNATURE - the backend's own documented
// test hook, only ever sent when the backend itself reports the mock gateway (its checkout key is
// not a real `rzp_` key). Against a real Razorpay backend this value is never used, and the backend
// would reject it anyway because it verifies the HMAC with its own secret.
const MOCK_GATEWAY_SIGNATURE = 'MOCK-VALID-SIGNATURE'

export type OnlinePaymentOutcome =
  | 'paid' // backend confirms SUCCESS
  | 'processing' // backend still PENDING (e.g. authorized, capture/webhook pending)
  | 'failed' // backend reports FAILED
  | 'cancelled' // customer closed Checkout without paying

export interface OnlinePaymentResult {
  outcome: OnlinePaymentOutcome
  payment: Payment
  message?: string
}

export interface OnlinePaymentContext {
  description: string
  prefill?: { contact?: string; email?: string; name?: string }
}

const errorMessage = (err: unknown, fallback: string): string =>
  (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/** Re-reads the payment from the backend - the only source of truth for its status. Polls briefly
 *  while it is still PENDING, since a genuine payment can take a moment to be captured. */
export async function refreshPayment(id: string, attempts = 4, delayMs = 1500): Promise<Payment> {
  let payment = (await paymentService.getById(id)).data.data
  for (let i = 1; i < attempts && payment.paymentStatus === 'PENDING'; i++) {
    await sleep(delayMs)
    payment = (await paymentService.getById(id)).data.data
  }
  return payment
}

const toResult = (payment: Payment, message?: string): OnlinePaymentResult => ({
  outcome: payment.paymentStatus === 'SUCCESS' ? 'paid' : payment.paymentStatus === 'FAILED' ? 'failed' : 'processing',
  payment,
  message,
})

/**
 * Collects an online payment for a payment-service payment that is already PENDING with a gateway
 * order (the result of POST /payments with paymentMethod RAZORPAY). Never decides the outcome
 * itself: whatever Checkout reports goes through POST /payments/{id}/verify, and the returned
 * status is whatever the backend reports afterwards.
 */
export async function completeOnlinePayment(
  payment: Payment,
  context: OnlinePaymentContext,
  pollDelayMs = 1500,
): Promise<OnlinePaymentResult> {
  if (!payment.gatewayOrderId) {
    throw new Error('This payment has no online checkout to complete.')
  }
  if (payment.paymentStatus === 'SUCCESS') {
    return { outcome: 'paid', payment }
  }

  if (!isRazorpayKey(payment.gatewayCheckoutKeyId)) {
    // Backend is running the mock gateway (local development): there is no real Checkout to open.
    let message: string | undefined
    try {
      await paymentService.verify(payment.id, {
        gatewayOrderId: payment.gatewayOrderId,
        gatewayPaymentId: `mock_pay_${payment.id.slice(0, 12)}`,
        signature: MOCK_GATEWAY_SIGNATURE,
      })
    } catch (err) {
      message = errorMessage(err, 'The payment could not be verified.')
    }
    return toResult(await refreshPayment(payment.id, 4, pollDelayMs), message)
  }

  const outcome = await openRazorpayCheckout({
    keyId: payment.gatewayCheckoutKeyId,
    gatewayOrderId: payment.gatewayOrderId,
    name: 'Farm2Home',
    description: context.description,
    prefill: context.prefill,
  })

  if (outcome.kind === 'dismissed') {
    // A webhook may already have settled it (e.g. the customer paid in a UPI app and closed
    // Checkout before it reported back), so check once rather than assume it was cancelled.
    const current = await refreshPayment(payment.id, 1, pollDelayMs)
    if (current.paymentStatus === 'SUCCESS') return toResult(current)
    if (current.paymentStatus === 'FAILED') return toResult(current, outcome.lastError)
    return {
      outcome: 'cancelled',
      payment: current,
      message: outcome.lastError
        ? `Payment was not completed: ${outcome.lastError}`
        : 'Payment was cancelled. No money was taken.',
    }
  }

  let message: string | undefined
  try {
    await paymentService.verify(payment.id, {
      gatewayOrderId: outcome.response.razorpay_order_id,
      gatewayPaymentId: outcome.response.razorpay_payment_id,
      signature: outcome.response.razorpay_signature,
    })
  } catch (err) {
    // Verification rejected or unreachable. If money was really collected, the webhook /
    // reconciliation job still records it, so report the backend's state rather than a failure.
    message = errorMessage(err, 'We could not confirm the payment yet.')
  }
  return toResult(await refreshPayment(payment.id, 4, pollDelayMs), message)
}
