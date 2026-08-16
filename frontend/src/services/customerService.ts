import axiosClient from './axiosClient'
import type {
  Customer, CustomerAddress, CreateAddressRequest, UpdateAddressRequest,
  UpdateCustomerRequest, CustomerSearchParams, DeliveryAvailability,
} from '../types/customer.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const SEARCH_RESULT_LIMIT = 5

const BASE = '/customers'

const ADDRESS_RETRY_ATTEMPTS = 4
const ADDRESS_RETRY_DELAY_MS = 500

/** The customer row behind this endpoint is created asynchronously (Kafka event, consumed by
 *  customer-service) right after auth-service's register() call, so calling this immediately
 *  after registering can 404 briefly until that event is consumed. Retries with a short backoff
 *  instead of failing on the first attempt. */
async function addAddress(data: CreateAddressRequest, attempt = 1): Promise<CustomerAddress> {
  try {
    const res = await axiosClient.post<ApiResponse<CustomerAddress>>(`${BASE}/me/addresses`, data)
    return res.data.data
  } catch (err: any) {
    if (err.response?.status === 404 && attempt < ADDRESS_RETRY_ATTEMPTS) {
      await new Promise((resolve) => setTimeout(resolve, ADDRESS_RETRY_DELAY_MS))
      return addAddress(data, attempt + 1)
    }
    throw err
  }
}

export const customerService = {
  getById: (id: string) =>
    axiosClient.get<ApiResponse<Customer>>(`${BASE}/${id}`),

  update: (id: string, data: UpdateCustomerRequest) =>
    axiosClient.put<ApiResponse<Customer>>(`${BASE}/${id}`, data),

  // GET /search requires SUPER_ADMIN/DELIVERY_MANAGER (see CustomerController) - only ever call
  // this for admin roles, e.g. from GlobalSearch, or it 403s for a plain customer.
  search: (keyword: string) =>
    axiosClient.get<ApiResponse<PageResponse<Customer>>>(`${BASE}/search`, {
      params: { keyword, size: SEARCH_RESULT_LIMIT },
    }),

  // Full server-side search/filter/pagination for the Customer Management list screen - same
  // endpoint as search() above, just with the full filter set and a real page size instead of
  // the typeahead's hardcoded limit.
  searchCustomers: (params: CustomerSearchParams) =>
    axiosClient.get<ApiResponse<PageResponse<Customer>>>(`${BASE}/search`, {
      params: { page: 0, size: 20, sort: 'createdAt,desc', ...params },
    }),

  getSummary: () =>
    axiosClient.get<ApiResponse<{ totalCustomers: number }>>(`${BASE}/summary`),

  // Soft-deletes the customer outright (SUPER_ADMIN only, and only once status is no longer
  // ACTIVE) - kept for completeness but not currently wired into the UI, which only offers the
  // reversible Activate/Suspend/Deactivate status actions (same reasoning as productService.delete).
  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  uploadProfileImage: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    // See productService.uploadImage for why Content-Type must be `undefined` here, not the
    // literal string 'multipart/form-data' - axios needs to generate the boundary itself.
    return axiosClient.post<ApiResponse<Customer>>(`${BASE}/${id}/profile-image`, formData, {
      headers: { 'Content-Type': undefined },
    })
  },

  export: async (params: CustomerSearchParams & { format: 'CSV' | 'EXCEL' | 'PDF' }) => {
    const res = await axiosClient.get(`${BASE}/export`, { params, responseType: 'blob' })
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `customers.${params.format.toLowerCase()}`
    return { blob: res.data as Blob, filename }
  },

  // Self-service - saves an address against the CALLER's own profile (id from the bearer token).
  // Used by the customer-facing registration flow; not for the admin dashboard's Customer
  // Management screens, which need to act on a specific OTHER customer - see the {id}-scoped
  // methods below for that.
  addAddress,

  getAddresses: (customerId: string) =>
    axiosClient.get<ApiResponse<CustomerAddress[]>>(`${BASE}/${customerId}/addresses`),

  addAddressForCustomer: (customerId: string, data: CreateAddressRequest) =>
    axiosClient.post<ApiResponse<CustomerAddress>>(`${BASE}/${customerId}/addresses`, data),

  updateAddress: (customerId: string, addressId: string, data: UpdateAddressRequest) =>
    axiosClient.put<ApiResponse<CustomerAddress>>(`${BASE}/${customerId}/addresses/${addressId}`, data),

  deleteAddress: (customerId: string, addressId: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${customerId}/addresses/${addressId}`),

  setDefaultAddress: (customerId: string, addressId: string) =>
    axiosClient.patch<ApiResponse<CustomerAddress>>(`${BASE}/${customerId}/addresses/${addressId}/default`),

  // Backend-authoritative 10 KM delivery-radius check (see order-service/customer-service's
  // Haversine calculation) - addressId omitted checks the caller's default address.
  getDeliveryAvailability: (addressId?: string) =>
    axiosClient.get<ApiResponse<DeliveryAvailability>>(`${BASE}/me/delivery-availability`, {
      params: addressId ? { addressId } : undefined,
    }),
}
