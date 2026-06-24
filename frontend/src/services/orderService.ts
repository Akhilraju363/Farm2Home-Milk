import axiosClient from './axiosClient'
import type { Order } from '../types/order.types'
import type { PageResponse } from '../types/common.types'

const BASE = '/orders'

export const orderService = {
  getAll: (params: { page?: number; size?: number; status?: string; orderDate?: string }) =>
    axiosClient.get<PageResponse<Order>>(BASE, { params: { page: 0, size: 20, ...params } }),

  getById: (id: string) =>
    axiosClient.get<Order>(`${BASE}/${id}`),

  create: (data: Partial<Order>) =>
    axiosClient.post<Order>(BASE, data),

  cancel: (id: string) =>
    axiosClient.put(`${BASE}/${id}/cancel`),
}
