import axiosClient from './axiosClient'
import type {
  CreateSubscriptionRequest, PauseSubscriptionRequest, Subscription, SubscriptionSearchParams,
  UpdateSubscriptionRequest,
} from '../types/subscription.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/subscriptions'

export const subscriptionService = {
  // /search (not the plain list endpoint) is used for every list fetch - keyword/status/milkType/
  // date-range/customerId (admin-only) filters all live there; a bare GET /subscriptions only
  // supports the admin customerId filter. Non-admin callers are scoped server-side to their own
  // subscriptions regardless of the customerId param (see SubscriptionController.search).
  search: (params: SubscriptionSearchParams) =>
    axiosClient.get<ApiResponse<PageResponse<Subscription>>>(`${BASE}/search`, {
      params: { page: 0, size: 20, sort: 'startDate,desc', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Subscription>>(`${BASE}/${id}`),

  // Used by Customer Details, one request per view, not per row.
  getByCustomer: (customerId: string, size = 5) =>
    axiosClient.get<ApiResponse<PageResponse<Subscription>>>(BASE, {
      params: { customerId, page: 0, size, sort: 'createdAt,desc' },
    }),

  create: (data: CreateSubscriptionRequest) =>
    axiosClient.post<ApiResponse<Subscription>>(BASE, data),

  update: (id: string, data: UpdateSubscriptionRequest) =>
    axiosClient.put<ApiResponse<Subscription>>(`${BASE}/${id}`, data),

  cancel: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  pause: (id: string, data: PauseSubscriptionRequest) =>
    axiosClient.post<ApiResponse<Subscription>>(`${BASE}/${id}/pause`, data),

  resume: (id: string) =>
    axiosClient.post<ApiResponse<Subscription>>(`${BASE}/${id}/resume`),

  export: async (params: SubscriptionSearchParams & { format: 'CSV' | 'EXCEL' | 'PDF' }) => {
    const res = await axiosClient.get(`${BASE}/export`, { params, responseType: 'blob' })
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `subscriptions.${params.format.toLowerCase()}`
    return { blob: res.data as Blob, filename }
  },
}
