import axiosClient from './axiosClient'
import type { Payment, Wallet, WalletTransaction } from '../types/payment.types'
import type { PageResponse } from '../types/common.types'

const BASE = '/payments'
const WALLET_BASE = '/wallets'

export const paymentService = {
  getAll: (params: { page?: number; size?: number; status?: string; paymentMethod?: string }) =>
    axiosClient.get<PageResponse<Payment>>(BASE, { params: { page: 0, size: 20, ...params } }),

  getById: (id: string) =>
    axiosClient.get<Payment>(`${BASE}/${id}`),

  initiate: (data: { orderId: string; paymentMethod: string; amount: number }) =>
    axiosClient.post<Payment>(BASE, data),

  refund: (id: string) =>
    axiosClient.post(`${BASE}/${id}/refund`),

  getWallet: (customerId: string) =>
    axiosClient.get<Wallet>(`${WALLET_BASE}/${customerId}`),

  getWalletTransactions: (customerId: string, page = 0, size = 20) =>
    axiosClient.get<PageResponse<WalletTransaction>>(`${WALLET_BASE}/${customerId}/transactions`, {
      params: { page, size },
    }),

  topUpWallet: (customerId: string, amount: number) =>
    axiosClient.post(`${WALLET_BASE}/${customerId}/top-up`, { amount }),
}
