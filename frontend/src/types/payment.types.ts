// Mirrors payment-service's PaymentResponse exactly (dto/response/PaymentResponse.java).
// gatewayResponse/gatewayOrderId/gatewayPaymentId/gatewayCheckoutKeyId/paidAt are all
// @JsonInclude(NON_NULL) on the backend, so they're optional here too. Note: the response DTO
// does NOT expose updatedAt or gatewayRefundId, even though the entity has both - don't invent
// them into the UI.
export interface Payment {
  id: string
  orderId: string
  customerId: string
  paymentReference: string
  amount: number
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  gatewayResponse?: string
  gatewayOrderId?: string
  gatewayPaymentId?: string
  gatewayCheckoutKeyId?: string
  paidAt?: string
  createdAt: string
}

export type PaymentMethod = 'UPI' | 'RAZORPAY' | 'WALLET' | 'CASH'
export const PAYMENT_METHODS: PaymentMethod[] = ['UPI', 'RAZORPAY', 'WALLET', 'CASH']
// Backend method names are the source of truth - UPI and RAZORPAY are presented as distinct
// selectable methods here because that's what InitiatePaymentRequest.paymentMethod accepts, even
// though both currently route through the same configured gateway provider server-side.
export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  UPI: 'UPI',
  RAZORPAY: 'Razorpay',
  WALLET: 'Wallet',
  CASH: 'Cash on Delivery',
}

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED'
export const PAYMENT_STATUSES: PaymentStatus[] = ['PENDING', 'SUCCESS', 'FAILED', 'REFUNDED']
export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pending',
  SUCCESS: 'Success',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
}

// Mirrors WalletResponse exactly.
export interface Wallet {
  id: string
  customerId: string
  balance: number
  updatedAt: string
}

// Mirrors WalletTransactionResponse exactly - no walletId field on the response (the wallet is
// always the caller's own, resolved server-side), referenceId is optional (null for a plain
// top-up, set for a payment-debit or refund-credit).
export interface WalletTransaction {
  id: string
  transactionType: 'CREDIT' | 'DEBIT'
  amount: number
  referenceId?: string
  description?: string
  createdAt: string
}

export interface InitiatePaymentRequest {
  customerId?: string // admin-only - omit to charge the authenticated caller
  orderId: string
  amount: number
  paymentMethod: PaymentMethod
  notes?: string
}

export interface VerifyPaymentRequest {
  gatewayOrderId: string
  gatewayPaymentId: string
  signature: string
}

export interface TopUpWalletRequest {
  amount: number
  description?: string
}

// GET /payments/reports and /payments/export share this exact filter set - GET /payments
// (the plain list) supports NO filters at all beyond pagination, only these two admin endpoints do.
export interface PaymentReportParams {
  dateFrom?: string
  dateTo?: string
  status?: PaymentStatus
  customerId?: string
  page?: number
  size?: number
  sort?: string
}

// Mirrors common-core's PaymentSummaryResponse exactly - just these two fields, nothing else.
export interface PaymentSummary {
  revenueToday: number
  revenueThisMonth: number
}

// Mirrors common-core's PaymentReportRow exactly.
export interface PaymentReportRow {
  paymentId: string
  orderId: string
  customerId: string
  amount: number
  paymentMethod: string
  paymentStatus: string
  paidAt?: string
  createdAt: string
}

// Mirrors common-core's PaymentReportSummary exactly.
export interface PaymentReportSummary {
  totalPayments: number
  totalAmount: number
  successAmount: number
}

// Mirrors common-core's ReportPage<T, S> exactly.
export interface ReportPage<T, S> {
  content: T[]
  pageNumber: number
  pageSize: number
  totalElements: number
  totalPages: number
  summary: S
}

export type Granularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'
export const GRANULARITIES: Granularity[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY']
export const GRANULARITY_LABELS: Record<Granularity, string> = {
  DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly',
}

// Mirrors common-core's RevenueTrendPoint exactly - SUM of SUCCESS amounts only.
export interface RevenueTrendPoint {
  period: string
  revenue: number
}

// Mirrors common-core's PaymentAnalyticsPoint exactly - volume/outcome mix, all statuses.
export interface PaymentAnalyticsPoint {
  period: string
  totalPayments: number
  totalAmount: number
  successCount: number
  failedCount: number
}

// Mirrors common-core's TrendSeries<T> exactly.
export interface TrendSeries<T> {
  granularity: Granularity
  from?: string
  to?: string
  points: T[]
}
