import axiosClient from './axiosClient'
import type { IndiaState, IndiaDistrict, IndiaCity } from '../types/indiaLocation.types'
import type { ApiResponse } from '../types/common.types'

const BASE = '/locations'

// Read-only India state/district/city reference data (LocationController, customer-service) -
// every dropdown value the registration Address step shows comes from these calls, never from a
// local constant. Distinct from services/locationService.ts, which submits/reads GPS delivery
// tracking pings - unrelated concept.
export const indiaLocationService = {
  getStates: () => axiosClient.get<ApiResponse<IndiaState[]>>(`${BASE}/states`),

  getDistricts: (stateId: string) =>
    axiosClient.get<ApiResponse<IndiaDistrict[]>>(`${BASE}/states/${stateId}/districts`),

  getCities: (districtId: string) =>
    axiosClient.get<ApiResponse<IndiaCity[]>>(`${BASE}/districts/${districtId}/cities`),
}
