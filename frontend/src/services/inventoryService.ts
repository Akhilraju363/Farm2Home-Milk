import axiosClient from './axiosClient'
import type { InventoryItem, StockTransaction } from '../types/inventory.types'
import type { PageResponse } from '../types/common.types'

const BASE = '/inventory'

export const inventoryService = {
  getAll: (params: { page?: number; size?: number; type?: string }) =>
    axiosClient.get<PageResponse<InventoryItem>>(BASE, { params: { page: 0, size: 50, ...params } }),

  getById: (id: string) =>
    axiosClient.get<InventoryItem>(`${BASE}/${id}`),

  getLowStock: () =>
    axiosClient.get<InventoryItem[]>(`${BASE}/low-stock`),

  create: (data: Partial<InventoryItem>) =>
    axiosClient.post<InventoryItem>(BASE, data),

  update: (id: string, data: Partial<InventoryItem>) =>
    axiosClient.put<InventoryItem>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete(`${BASE}/${id}`),

  stockIn: (itemId: string, quantity: number, reason?: string) =>
    axiosClient.post<StockTransaction>(`${BASE}/${itemId}/transactions`, {
      txnType: 'IN', quantity, reason,
    }),

  stockOut: (itemId: string, quantity: number, reason?: string) =>
    axiosClient.post<StockTransaction>(`${BASE}/${itemId}/transactions`, {
      txnType: 'OUT', quantity, reason,
    }),

  getTransactions: (itemId: string, page = 0) =>
    axiosClient.get<PageResponse<StockTransaction>>(`${BASE}/${itemId}/transactions`, {
      params: { page, size: 20 },
    }),
}
