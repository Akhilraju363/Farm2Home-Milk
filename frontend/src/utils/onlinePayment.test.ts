import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Payment, PaymentStatus } from '../types/payment.types'

vi.mock('../services/paymentService', () => ({
  paymentService: { verify: vi.fn(), getById: vi.fn() },
}))
vi.mock('./razorpayCheckout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./razorpayCheckout')>()),
  openRazorpayCheckout: vi.fn(),
}))

import { paymentService } from '../services/paymentService'
import { openRazorpayCheckout } from './razorpayCheckout'
import { completeOnlinePayment } from './onlinePayment'

const verify = vi.mocked(paymentService.verify)
const getById = vi.mocked(paymentService.getById)
const openCheckout = vi.mocked(openRazorpayCheckout)

const payment = (overrides: Partial<Payment> = {}): Payment => ({
  id: 'pay-uuid-0000-1111',
  orderId: 'order-1',
  customerId: 'cust-1',
  paymentReference: 'PAY-1',
  amount: 150,
  paymentMethod: 'RAZORPAY',
  paymentStatus: 'PENDING',
  gatewayOrderId: 'order_ABC',
  gatewayCheckoutKeyId: 'rzp_test_public',
  createdAt: '2026-09-27T10:00:00',
  ...overrides,
})

/** getById answers with these statuses in order (the last one repeats). */
const backendReports = (...statuses: PaymentStatus[]) => {
  let i = 0
  getById.mockImplementation(async () => {
    const status = statuses[Math.min(i++, statuses.length - 1)]
    return { data: { data: payment({ paymentStatus: status }) } } as any
  })
}

const context = { description: 'Order ORD-1' }

beforeEach(() => {
  vi.clearAllMocks()
})

describe('completeOnlinePayment', () => {
  it('verifies the Checkout response server-side and reports the backend status', async () => {
    openCheckout.mockResolvedValue({
      kind: 'success',
      response: { razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig' },
    })
    verify.mockResolvedValue({} as any)
    backendReports('SUCCESS')

    const result = await completeOnlinePayment(payment(), context, 0)

    expect(verify).toHaveBeenCalledWith('pay-uuid-0000-1111', {
      gatewayOrderId: 'order_ABC', gatewayPaymentId: 'pay_1', signature: 'sig',
    })
    expect(result.outcome).toBe('paid')
  })

  it('never reports success from Checkout alone - a backend that is still PENDING means processing', async () => {
    openCheckout.mockResolvedValue({
      kind: 'success',
      response: { razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig' },
    })
    verify.mockResolvedValue({ data: { data: payment({ paymentStatus: 'SUCCESS' }) } } as any)
    backendReports('PENDING')

    const result = await completeOnlinePayment(payment(), context, 0)

    expect(result.outcome).toBe('processing')
  })

  it('a rejected verification (e.g. invalid signature) is surfaced and the backend status is kept', async () => {
    openCheckout.mockResolvedValue({
      kind: 'success',
      response: { razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'forged' },
    })
    verify.mockRejectedValue({ response: { data: { message: 'Payment verification failed: the payment signature is invalid.' } } })
    backendReports('PENDING')

    const result = await completeOnlinePayment(payment(), context, 0)

    expect(result.outcome).toBe('processing')
    expect(result.message).toContain('signature is invalid')
  })

  it('backend-confirmed failure → failed', async () => {
    openCheckout.mockResolvedValue({
      kind: 'success',
      response: { razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_ABC', razorpay_signature: 'sig' },
    })
    verify.mockResolvedValue({} as any)
    backendReports('FAILED')

    expect((await completeOnlinePayment(payment(), context, 0)).outcome).toBe('failed')
  })

  it('dismissed Checkout → cancelled, and nothing is sent for verification', async () => {
    openCheckout.mockResolvedValue({ kind: 'dismissed' })
    backendReports('PENDING')

    const result = await completeOnlinePayment(payment(), context, 0)

    expect(result.outcome).toBe('cancelled')
    expect(verify).not.toHaveBeenCalled()
  })

  it('dismissed after failed attempts → cancelled with the gateway reason', async () => {
    openCheckout.mockResolvedValue({ kind: 'dismissed', lastError: 'Card declined' })
    backendReports('PENDING')

    const result = await completeOnlinePayment(payment(), context, 0)

    expect(result.outcome).toBe('cancelled')
    expect(result.message).toContain('Card declined')
  })

  it('dismissed but a webhook already confirmed the payment → paid', async () => {
    openCheckout.mockResolvedValue({ kind: 'dismissed' })
    backendReports('SUCCESS')

    expect((await completeOnlinePayment(payment(), context, 0)).outcome).toBe('paid')
  })

  it('mock gateway (local dev) → no Checkout, uses the backend test verification hook', async () => {
    verify.mockResolvedValue({} as any)
    backendReports('SUCCESS')

    const result = await completeOnlinePayment(payment({ gatewayCheckoutKeyId: 'mock_checkout_key' }), context, 0)

    expect(openCheckout).not.toHaveBeenCalled()
    expect(verify).toHaveBeenCalledWith('pay-uuid-0000-1111', expect.objectContaining({ signature: 'MOCK-VALID-SIGNATURE' }))
    expect(result.outcome).toBe('paid')
  })

  it('an already-successful payment is not collected twice', async () => {
    const result = await completeOnlinePayment(payment({ paymentStatus: 'SUCCESS' }), context, 0)

    expect(result.outcome).toBe('paid')
    expect(openCheckout).not.toHaveBeenCalled()
    expect(verify).not.toHaveBeenCalled()
  })
})
