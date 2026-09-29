import { afterEach, describe, expect, it, vi } from 'vitest'
import { isRazorpayKey, openRazorpayCheckout, resetRazorpayCheckoutForTests } from './razorpayCheckout'
import type { RazorpayConstructor } from './razorpayCheckout'

type Handlers = {
  options: Record<string, any>
  failed?: (r: { error?: { description?: string } }) => void
}

/** Installs a fake window.Razorpay and returns a handle to drive the Checkout it opens. */
function installFakeRazorpay() {
  const handle: Handlers = { options: {} }
  const Fake = vi.fn().mockImplementation((options: Record<string, any>) => {
    handle.options = options
    return {
      open: vi.fn(),
      on: (_event: string, cb: Handlers['failed']) => { handle.failed = cb },
    }
  })
  window.Razorpay = Fake as unknown as RazorpayConstructor
  return handle
}

const baseOptions = {
  keyId: 'rzp_test_public',
  gatewayOrderId: 'order_ABC',
  name: 'Farm2Home',
  description: 'Order ORD-1',
}

afterEach(() => {
  delete window.Razorpay
  resetRazorpayCheckoutForTests()
})

describe('isRazorpayKey', () => {
  it('accepts real Razorpay key ids only', () => {
    expect(isRazorpayKey('rzp_test_abc')).toBe(true)
    expect(isRazorpayKey('rzp_live_abc')).toBe(true)
    expect(isRazorpayKey('mock_checkout_key')).toBe(false)
    expect(isRazorpayKey(undefined)).toBe(false)
  })
})

describe('openRazorpayCheckout', () => {
  it('opens Checkout for the server order with the public key and no amount', async () => {
    const rzp = installFakeRazorpay()
    const pending = openRazorpayCheckout(baseOptions)
    await vi.waitFor(() => expect(rzp.options.order_id).toBe('order_ABC'))

    expect(rzp.options.key).toBe('rzp_test_public')
    expect(rzp.options).not.toHaveProperty('amount')
    rzp.options.handler({ razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig' })

    await expect(pending).resolves.toEqual({
      kind: 'success',
      response: { razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig' },
    })
  })

  it('settles once even if Checkout reports success twice or is dismissed afterwards', async () => {
    const rzp = installFakeRazorpay()
    const pending = openRazorpayCheckout(baseOptions)
    await vi.waitFor(() => expect(rzp.options.handler).toBeTypeOf('function'))

    rzp.options.handler({ razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig' })
    rzp.options.handler({ razorpay_payment_id: 'pay_2', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig2' })
    rzp.options.modal.ondismiss()

    const outcome = await pending
    expect(outcome).toMatchObject({ kind: 'success', response: { razorpay_payment_id: 'pay_1' } })
  })

  it('reports dismissal, carrying the last failed attempt reason', async () => {
    const rzp = installFakeRazorpay()
    const pending = openRazorpayCheckout(baseOptions)
    await vi.waitFor(() => expect(rzp.failed).toBeTypeOf('function'))

    rzp.failed!({ error: { description: 'Card declined' } })
    rzp.options.modal.ondismiss()

    await expect(pending).resolves.toEqual({ kind: 'dismissed', lastError: 'Card declined' })
  })

  it('reports a plain cancellation when the customer closes Checkout without trying', async () => {
    const rzp = installFakeRazorpay()
    const pending = openRazorpayCheckout(baseOptions)
    await vi.waitFor(() => expect(rzp.options.modal).toBeDefined())

    rzp.options.modal.ondismiss()

    await expect(pending).resolves.toEqual({ kind: 'dismissed', lastError: undefined })
  })
})
