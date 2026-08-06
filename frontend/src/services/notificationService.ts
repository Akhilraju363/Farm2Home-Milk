import axiosClient from './axiosClient'
import type { ApiResponse } from '../types/common.types'
import type { NotificationSummaryItem } from '../types/notification.types'

const BASE = '/notifications'

export const notificationService = {
  // Ops-wide feed across every recipient - admin roles only (matches the endpoint's own
  // @PreAuthorize), since it would otherwise leak other customers' notifications.
  getSummary: (limit = 10) =>
    axiosClient.get<ApiResponse<NotificationSummaryItem[]>>(`${BASE}/summary`, { params: { limit } }),

  // Self-service equivalent, scoped server-side to the caller's own notifications only - safe
  // for any authenticated role, including plain customers.
  getMine: (limit = 10) =>
    axiosClient.get<ApiResponse<NotificationSummaryItem[]>>(`${BASE}/me`, { params: { limit } }),

  markAsRead: (id: string) =>
    axiosClient.patch<ApiResponse<void>>(`${BASE}/${id}/read`),

  markAllAsRead: () =>
    axiosClient.patch<ApiResponse<void>>(`${BASE}/read-all`),
}
