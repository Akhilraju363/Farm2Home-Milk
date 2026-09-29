// Thin wrapper around Razorpay Checkout (https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/).
// Checkout itself offers every method enabled on the Razorpay account - cards, UPI (including the
// Google Pay / PhonePe / Paytm apps via UPI intent/collect), netbanking and wallets - so this app
// never integrates with any of those individually.
//
// Only the PUBLIC key id ever reaches the browser (payment-service returns it as
// gatewayCheckoutKeyId). The Checkout result is never treated as proof of payment: it is handed to
// POST /payments/{id}/verify, which checks the signature and re-fetches the payment from Razorpay.

const CHECKOUT_SCRIPT_SRC = 'https://checkout.razorpay.com/v1/checkout.js'

/** What Checkout passes to `handler` after a successful payment. */
export interface RazorpaySuccessResponse {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}

interface RazorpayFailureResponse {
  error?: { description?: string; reason?: string }
}

interface RazorpayInstance {
  open: () => void
  on: (event: 'payment.failed', callback: (response: RazorpayFailureResponse) => void) => void
}

export type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayInstance

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor
  }
}

export type CheckoutOutcome =
  | { kind: 'success'; response: RazorpaySuccessResponse }
  // The customer closed Checkout without completing a payment. `lastError` is set when at least
  // one attempt failed inside Checkout first (Checkout stays open and lets them retry, so a
  // failed attempt alone is not the end of the flow).
  | { kind: 'dismissed'; lastError?: string }

export interface CheckoutOptions {
  keyId: string
  gatewayOrderId: string
  name: string
  description: string
  prefill?: { contact?: string; email?: string; name?: string }
  themeColor?: string
}

/** A real Razorpay key id; anything else (e.g. the mock provider's placeholder) can't open Checkout. */
export const isRazorpayKey = (keyId?: string | null): keyId is string =>
  Boolean(keyId && keyId.startsWith('rzp_'))

let scriptPromise: Promise<void> | null = null

/** Loads checkout.js once per page. Resolves immediately if Razorpay is already available. */
export function loadRazorpayCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve()
  if (scriptPromise) return scriptPromise

  scriptPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CHECKOUT_SCRIPT_SRC
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      scriptPromise = null
      script.remove()
      reject(new Error('Could not load Razorpay Checkout. Check your connection and try again.'))
    }
    document.body.appendChild(script)
  })
  return scriptPromise
}

/** Opens Checkout for an existing Razorpay order and settles exactly once - on the first
 *  successful payment or on dismissal, whichever comes first. The amount is deliberately not
 *  passed: Checkout takes it from the server-created order, so the browser can't change it. */
export async function openRazorpayCheckout(options: CheckoutOptions): Promise<CheckoutOutcome> {
  await loadRazorpayCheckout()
  const Razorpay = window.Razorpay
  if (!Razorpay) {
    throw new Error('Razorpay Checkout is unavailable right now. Please try again.')
  }

  return new Promise<CheckoutOutcome>((resolve) => {
    let settled = false
    let lastError: string | undefined
    const settle = (outcome: CheckoutOutcome) => {
      if (settled) return
      settled = true
      resolve(outcome)
    }

    const rzp = new Razorpay({
      key: options.keyId,
      order_id: options.gatewayOrderId,
      name: options.name,
      description: options.description,
      prefill: options.prefill,
      theme: options.themeColor ? { color: options.themeColor } : undefined,
      handler: (response: RazorpaySuccessResponse) => settle({ kind: 'success', response }),
      modal: {
        // Keep the customer from dismissing Checkout by accident mid-payment (e.g. while
        // switching to a UPI app) - they close it deliberately.
        escape: false,
        backdropclose: false,
        ondismiss: () => settle({ kind: 'dismissed', lastError }),
      },
    })
    rzp.on('payment.failed', (response) => {
      lastError = response.error?.description ?? 'The payment attempt failed.'
    })
    rzp.open()
  })
}

/** Test-only: forget the cached script load between tests. */
export function resetRazorpayCheckoutForTests() {
  scriptPromise = null
}
