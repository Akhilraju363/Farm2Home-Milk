import axiosClient from './axiosClient'
import type { ApiResponse, PageResponse } from '../types/common.types'
import type {
  BusinessSettings, CreateFarmRequest, Farm, FarmSearchParams, UpdateBusinessSettingsRequest, UpdateFarmRequest,
} from '../types/farm.types'

const BASE = '/farm'

export const farmService = {
  // /search (not the plain list endpoint) is used for every list fetch, same convention as
  // productService - it's the only endpoint that supports the keyword/date filters the page needs,
  // and with no params it behaves identically to plain GET /farm (see FarmController#search).
  search: (params: FarmSearchParams = {}) =>
    axiosClient.get<ApiResponse<PageResponse<Farm>>>(`${BASE}/search`, {
      params: { page: 0, size: 20, ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Farm>>(`${BASE}/${id}`),

  create: (data: CreateFarmRequest) =>
    axiosClient.post<ApiResponse<Farm>>(BASE, data),

  update: (id: string, data: UpdateFarmRequest) =>
    axiosClient.put<ApiResponse<Farm>>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  uploadImage: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    // axiosClient defaults to 'Content-Type: application/json' - overriding to undefined (not the
    // literal string 'multipart/form-data') lets axios auto-generate the header with the boundary.
    return axiosClient.post<ApiResponse<Farm>>(`${BASE}/${id}/image`, formData, {
      headers: { 'Content-Type': undefined },
    })
  },

  // Farm2Home's own delivery origin + radius - GET is open to any authenticated caller, PUT is
  // FARM_MANAGER/SUPER_ADMIN only (see FarmController#updateBusinessSettings).
  getBusinessSettings: () =>
    axiosClient.get<ApiResponse<BusinessSettings>>(`${BASE}/business-settings`),

  updateBusinessSettings: (data: UpdateBusinessSettingsRequest) =>
    axiosClient.put<ApiResponse<BusinessSettings>>(`${BASE}/business-settings`, data),
}
