import axiosClient from './axiosClient'
import type {
  InitiatePaymentRequest, Payment, PaymentAnalyticsPoint, PaymentReportParams, PaymentReportRow,
  PaymentReportSummary, PaymentSummary, ReportPage, RevenueTrendPoint, TrendSeries, VerifyPaymentRequest,
  Granularity,
} from '../types/payment.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/payments'

export const paymentService = {
  // GET /payments supports NO filters at all, only page/size/sort - admin sees every payment,
  // non-admin is scoped server-side to their own customerId. Used for the customer's plain
  // "My Payments" list; the admin table uses getReport() below instead, since that's the only
  // endpoint offering real filters.
  getAll: (params: { page?: number; size?: number; sort?: string } = {}) =>
    axiosClient.get<ApiResponse<PageResponse<Payment>>>(BASE, {
      params: { page: 0, size: 20, sort: 'createdAt,desc', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Payment>>(`${BASE}/${id}`),

  // Every payment attempt for an order (an order can have more than one if an earlier attempt
  // failed). Used by Order Details, one request, not per-row.
  getByOrder: (orderId: string) =>
    axiosClient.get<ApiResponse<Payment[]>>(`${BASE}/order/${orderId}`),

  // Lightweight boolean check, open to any authenticated caller - used to decide whether Order
  // Details should show "Pay Now" or "Already Paid" without fetching full payment details.
  hasPayableProgress: (orderId: string) =>
    axiosClient.get<ApiResponse<boolean>>(`${BASE}/order/${orderId}/payment-exists`),

  initiate: (data: InitiatePaymentRequest) =>
    axiosClient.post<ApiResponse<Payment>>(BASE, data),

  verify: (id: string, data: VerifyPaymentRequest) =>
    axiosClient.post<ApiResponse<Payment>>(`${BASE}/${id}/verify`, data),

  // FARM_MANAGER/SUPER_ADMIN only on the backend - full-amount refund, no request body.
  refund: (id: string) =>
    axiosClient.post<ApiResponse<Payment>>(`${BASE}/${id}/refund`),

  // SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER only.
  getSummary: () =>
    axiosClient.get<ApiResponse<PaymentSummary>>(`${BASE}/summary`),

  getReport: (params: PaymentReportParams) =>
    axiosClient.get<ApiResponse<ReportPage<PaymentReportRow, PaymentReportSummary>>>(`${BASE}/reports`, {
      params: { page: 0, size: 20, ...params },
    }),

  export: async (params: PaymentReportParams & { format: 'CSV' | 'EXCEL' | 'PDF' }) => {
    const res = await axiosClient.get(`${BASE}/export`, { params, responseType: 'blob' })
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `payments.${params.format.toLowerCase()}`
    return { blob: res.data as Blob, filename }
  },

  getRevenueTrend: (granularity: Granularity, dateFrom?: string, dateTo?: string) =>
    axiosClient.get<ApiResponse<TrendSeries<RevenueTrendPoint>>>(`${BASE}/analytics/revenue-trend`, {
      params: { granularity, dateFrom, dateTo },
    }),

  getPaymentAnalytics: (granularity: Granularity, dateFrom?: string, dateTo?: string) =>
    axiosClient.get<ApiResponse<TrendSeries<PaymentAnalyticsPoint>>>(`${BASE}/analytics/payment-trend`, {
      params: { granularity, dateFrom, dateTo },
    }),
}
