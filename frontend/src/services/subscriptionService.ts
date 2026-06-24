import axiosClient from './axiosClient'
import type { Subscription } from '../types/subscription.types'
import type { PageResponse } from '../types/common.types'

const BASE = '/subscriptions'

export const subscriptionService = {
  getAll: (params: { page?: number; size?: number; status?: string; customerId?: string }) =>
    axiosClient.get<PageResponse<Subscription>>(BASE, { params: { page: 0, size: 20, ...params } }),

  getById: (id: string) =>
    axiosClient.get<Subscription>(`${BASE}/${id}`),

  create: (data: Partial<Subscription>) =>
    axiosClient.post<Subscription>(BASE, data),

  update: (id: string, data: Partial<Subscription>) =>
    axiosClient.put<Subscription>(`${BASE}/${id}`, data),

  pause: (id: string, data: { pauseStart: string; pauseEnd: string }) =>
    axiosClient.put(`${BASE}/${id}/pause`, data),

  resume: (id: string) =>
    axiosClient.put(`${BASE}/${id}/resume`),

  cancel: (id: string) =>
    axiosClient.put(`${BASE}/${id}/cancel`),
}
