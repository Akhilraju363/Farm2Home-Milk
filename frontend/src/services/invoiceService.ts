import axiosClient from './axiosClient'
import type { ApiResponse, PageResponse } from '../types/common.types'
import type { Invoice, InvoiceSearchParams, InvoiceSummary } from '../types/invoice.types'

const BASE = '/invoices'

export const invoiceService = {
  generate: (orderId: string) =>
    axiosClient.post<ApiResponse<Invoice>>(`${BASE}/generate/${orderId}`),

  search: (params: InvoiceSearchParams = {}) =>
    axiosClient.get<ApiResponse<PageResponse<InvoiceSummary>>>(BASE, {
      params: { page: 0, size: 20, ...params },
    }),

  getMine: (params: { page?: number; size?: number } = {}) =>
    axiosClient.get<ApiResponse<PageResponse<InvoiceSummary>>>(`${BASE}/me`, {
      params: { page: 0, size: 20, ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Invoice>>(`${BASE}/${id}`),

  // 404 (not an error state to alarm on) means no invoice has been generated for this order yet -
  // callers treat that as the normal "no invoice" case, same convention as paymentService.getByOrder.
  getByOrder: (orderId: string) =>
    axiosClient.get<ApiResponse<Invoice>>(`${BASE}/order/${orderId}`),

  downloadPdf: async (id: string) => {
    const res = await axiosClient.get(`${BASE}/${id}/pdf`, { responseType: 'blob' })
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `invoice-${id}.pdf`
    return { blob: res.data as Blob, filename }
  },
}
