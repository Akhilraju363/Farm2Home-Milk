import axiosClient from './axiosClient'
import type { MilkProduction, DailySummary } from '../types/production.types'
import type { PageResponse } from '../types/common.types'

const BASE = '/productions'

export const productionService = {
  getAll: (params: { page?: number; size?: number }) =>
    axiosClient.get<PageResponse<MilkProduction>>(BASE, { params: { page: 0, size: 50, ...params } }),

  getById: (id: string) =>
    axiosClient.get<MilkProduction>(`${BASE}/${id}`),

  create: (data: Partial<MilkProduction>) =>
    axiosClient.post<MilkProduction>(BASE, data),

  update: (id: string, data: Partial<MilkProduction>) =>
    axiosClient.put<MilkProduction>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete(`${BASE}/${id}`),

  getDailySummary: (from: string, to: string) =>
    axiosClient.get<DailySummary[]>(`${BASE}/summary`, { params: { from, to } }),

  getCowSummary: (cowId: string, from: string, to: string) =>
    axiosClient.get<DailySummary[]>(`${BASE}/cow/${cowId}/summary`, { params: { from, to } }),
}
