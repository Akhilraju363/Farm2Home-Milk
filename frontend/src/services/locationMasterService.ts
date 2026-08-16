import axiosClient from './axiosClient'
import type { ApiResponse } from '../types/common.types'

export type LocationKind = 'states' | 'districts' | 'cities'
export interface LocationMasterItem { id: string; name: string; code?: string; active: boolean; stateId?: string; districtId?: string; stateName?: string; districtName?: string }
export interface LocationMasterPayload { name: string; code?: string; stateId?: string; districtId?: string; active?: boolean }
const base = '/location-master'
export const locationMasterService = {
  list: (kind: LocationKind, params?: Record<string, string | number | boolean | undefined>) => axiosClient.get<ApiResponse<LocationMasterItem[]>>(`${base}/${kind}`, { params }),
  create: (kind: LocationKind, payload: LocationMasterPayload) => axiosClient.post<ApiResponse<LocationMasterItem>>(`${base}/${kind}`, payload),
  update: (kind: LocationKind, id: string, payload: LocationMasterPayload) => axiosClient.put<ApiResponse<LocationMasterItem>>(`${base}/${kind}/${id}`, payload),
  delete: (kind: LocationKind, id: string) => axiosClient.delete<ApiResponse<void>>(`${base}/${kind}/${id}`),
}
