import axiosClient from './axiosClient'
import type {
  CreateOrderRequest, Order, OrderSearchParams, UpdateOrderStatusRequest,
} from '../types/order.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/orders'

export const orderService = {
  // GET /orders/search (not the plain list endpoint) is used for every list fetch - keyword/
  // status/milkType/date-range/customerId (admin-only) filters all live there. Non-admin callers
  // are scoped server-side to their own orders regardless of the customerId param.
  search: (params: OrderSearchParams) =>
    axiosClient.get<ApiResponse<PageResponse<Order>>>(`${BASE}/search`, {
      params: { page: 0, size: 20, sort: 'orderDate,desc', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Order>>(`${BASE}/${id}`),

  // Used by the dashboard's recent-orders list, one request, not per row.
  getRecent: (size = 5) =>
    axiosClient.get<ApiResponse<PageResponse<Order>>>(`${BASE}/search`, {
      params: { page: 0, size, sort: 'orderDate,desc' },
    }),

  // Used by Customer Details, one request per view, not per row.
  getByCustomer: (customerId: string, size = 5) =>
    axiosClient.get<ApiResponse<PageResponse<Order>>>(BASE, {
      params: { customerId, page: 0, size, sort: 'orderDate,desc' },
    }),

  // GET /orders/subscription/{subscriptionId} - every order the daily subscription-order job has
  // generated for one subscription. Used by Subscription Details, one request, not per row.
  // Ownership-checked server-side: a non-owner gets an empty page, not an error.
  getBySubscription: (subscriptionId: string, size = 10) =>
    axiosClient.get<ApiResponse<PageResponse<Order>>>(`${BASE}/subscription/${subscriptionId}`, {
      params: { page: 0, size },
    }),

  create: (data: CreateOrderRequest) =>
    axiosClient.post<ApiResponse<Order>>(BASE, data),

  updateStatus: (id: string, data: UpdateOrderStatusRequest) =>
    axiosClient.patch<ApiResponse<Order>>(`${BASE}/${id}/status`, data),

  cancel: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  export: async (params: OrderSearchParams & { format: 'CSV' | 'EXCEL' | 'PDF' }) => {
    const res = await axiosClient.get(`${BASE}/export`, { params, responseType: 'blob' })
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `orders.${params.format.toLowerCase()}`
    return { blob: res.data as Blob, filename }
  },
}
