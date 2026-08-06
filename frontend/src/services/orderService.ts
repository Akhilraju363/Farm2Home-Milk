import axiosClient from './axiosClient'
import type { Order } from '../types/order.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/orders'

export const orderService = {
  getAll: (params: { page?: number; size?: number; status?: string; orderDate?: string }) =>
    axiosClient.get<PageResponse<Order>>(BASE, { params: { page: 0, size: 20, ...params } }),

  // Correctly typed as ApiResponse<PageResponse<Order>> (unlike getAll above, whose generic
  // omits the ApiResponse envelope) - used where the extra .data unwrap actually matters, e.g.
  // the dashboard's most-recent-orders list.
  getRecent: (size = 5) =>
    axiosClient.get<ApiResponse<PageResponse<Order>>>(BASE, { params: { page: 0, size, sort: 'orderDate,desc' } }),

  getById: (id: string) =>
    axiosClient.get<Order>(`${BASE}/${id}`),

  create: (data: Partial<Order>) =>
    axiosClient.post<Order>(BASE, data),

  cancel: (id: string) =>
    axiosClient.put(`${BASE}/${id}/cancel`),

  search: (keyword: string) =>
    axiosClient.get<ApiResponse<PageResponse<Order>>>(`${BASE}/search`, { params: { keyword, size: 5 } }),
}
