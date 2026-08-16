import axiosClient from './axiosClient'
import type { InventoryItem, StockTransaction } from '../types/inventory.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/inventory'

// Every InventoryItemController response body is wrapped in the common ApiResponse envelope
// ({success, message, data, timestamp}) - matching orderService/subscriptionService/paymentService's
// established ApiResponse<PageResponse<T>> pattern, not a raw PageResponse/entity.
export const inventoryService = {
  getAll: (params: { page?: number; size?: number; type?: string }) =>
    axiosClient.get<ApiResponse<PageResponse<InventoryItem>>>(BASE, { params: { page: 0, size: 50, ...params } }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<InventoryItem>>(`${BASE}/${id}`),

  getLowStock: () =>
    axiosClient.get<ApiResponse<InventoryItem[]>>(`${BASE}/low-stock`),

  create: (data: Partial<InventoryItem>) =>
    axiosClient.post<ApiResponse<InventoryItem>>(BASE, data),

  update: (id: string, data: Partial<InventoryItem>) =>
    axiosClient.put<ApiResponse<InventoryItem>>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  stockIn: (itemId: string, quantity: number, reason?: string) =>
    axiosClient.post<ApiResponse<StockTransaction>>(`${BASE}/${itemId}/transactions`, {
      txnType: 'IN', quantity, reason,
    }),

  stockOut: (itemId: string, quantity: number, reason?: string) =>
    axiosClient.post<ApiResponse<StockTransaction>>(`${BASE}/${itemId}/transactions`, {
      txnType: 'OUT', quantity, reason,
    }),

  getTransactions: (itemId: string, page = 0) =>
    axiosClient.get<ApiResponse<PageResponse<StockTransaction>>>(`${BASE}/${itemId}/transactions`, {
      params: { page, size: 20 },
    }),
}
