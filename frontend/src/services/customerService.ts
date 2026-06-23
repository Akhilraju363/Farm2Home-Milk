import axiosClient from './axiosClient'
import type { Customer, CustomerAddress } from '../types/customer.types'
import type { PageResponse } from '../types/common.types'

const BASE = '/customers'

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

  getAddresses: (customerId: string) =>
    axiosClient.get<CustomerAddress[]>(`${BASE}/${customerId}/addresses`),

  addAddress: (customerId: string, data: Partial<CustomerAddress>) =>
    axiosClient.post<CustomerAddress>(`${BASE}/${customerId}/addresses`, data),
}
