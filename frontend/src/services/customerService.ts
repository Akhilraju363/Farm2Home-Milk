import axiosClient from './axiosClient'
import type { Customer, CustomerAddress, CreateAddressRequest } from '../types/customer.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

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
  getAll: (page = 0, size = 20) =>
    axiosClient.get<PageResponse<Customer>>(BASE, { params: { page, size } }),

  getById: (id: string) =>
    axiosClient.get<Customer>(`${BASE}/${id}`),

  create: (data: Partial<Customer>) =>
    axiosClient.post<Customer>(BASE, data),

  update: (id: string, data: Partial<Customer>) =>
    axiosClient.put<Customer>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete(`${BASE}/${id}`),

  addAddress,
}
