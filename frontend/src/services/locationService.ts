import axiosClient from './axiosClient'
import type { DeliveryLocation, SubmitLocationRequest } from '../types/location.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/delivery/assignments'

export const locationService = {
  submit: (assignmentId: string, data: SubmitLocationRequest) =>
    axiosClient.post<ApiResponse<DeliveryLocation>>(`${BASE}/${assignmentId}/location`, data),

  getCurrent: (assignmentId: string) =>
    axiosClient.get<ApiResponse<DeliveryLocation | null>>(`${BASE}/${assignmentId}/location`),

  getHistory: (assignmentId: string, params?: { page?: number; size?: number }) =>
    axiosClient.get<ApiResponse<PageResponse<DeliveryLocation>>>(`${BASE}/${assignmentId}/locations`, { params }),
}
