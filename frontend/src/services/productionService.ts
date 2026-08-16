import axiosClient from './axiosClient'
import type { MilkProduction, DailySummary } from '../types/production.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/productions'

// Every MilkProductionController response body is wrapped in the common ApiResponse envelope
// ({success, message, data, timestamp}) - matching orderService/subscriptionService/paymentService's
// established ApiResponse<PageResponse<T>> pattern, not a raw PageResponse/array/entity.
export const productionService = {
  getAll: (params: { page?: number; size?: number }) =>
    axiosClient.get<ApiResponse<PageResponse<MilkProduction>>>(BASE, { params: { page: 0, size: 50, ...params } }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<MilkProduction>>(`${BASE}/${id}`),

  create: (data: Partial<MilkProduction>) =>
    axiosClient.post<ApiResponse<MilkProduction>>(BASE, data),

  update: (id: string, data: Partial<MilkProduction>) =>
    axiosClient.put<ApiResponse<MilkProduction>>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  getDailySummary: (from: string, to: string) =>
    axiosClient.get<ApiResponse<DailySummary[]>>(`${BASE}/summary`, { params: { from, to } }),

  getCowSummary: (cowId: string, from: string, to: string) =>
    axiosClient.get<ApiResponse<DailySummary[]>>(`${BASE}/cow/${cowId}/summary`, { params: { from, to } }),
}
