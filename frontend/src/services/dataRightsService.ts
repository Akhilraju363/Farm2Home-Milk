import axiosClient from './axiosClient'
import type { ApiResponse, PageResponse } from '../types/common.types'
import type { CreateDataRightsRequest, DataRightsRequest } from '../types/dataRights.types'

const BASE = '/data-rights-requests'

export const dataRightsService = {
  // Public - no auth required, matches the backend's deliberately public POST /submit (see
  // customer-service's SecurityConfig/DataRightsRequestController).
  submit: (data: CreateDataRightsRequest) =>
    axiosClient.post<ApiResponse<DataRightsRequest>>(`${BASE}/submit`, data),

  // Admin (SUPER_ADMIN) only - see DataRightsRequestController.
  findAll: (params?: { page?: number; size?: number }) =>
    axiosClient.get<ApiResponse<PageResponse<DataRightsRequest>>>(BASE, { params: { page: 0, size: 20, ...params } }),
}
