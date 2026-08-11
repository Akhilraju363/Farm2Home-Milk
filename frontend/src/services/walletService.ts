import axiosClient from './axiosClient'
import type { TopUpWalletRequest, Wallet, WalletTransaction } from '../types/payment.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/wallets'

// Every endpoint here is self-scoped to the authenticated caller's own wallet server-side
// (resolved from the JWT, never a path/query parameter) - there is no admin "view any customer's
// wallet" endpoint, so this service has no customerId parameter anywhere.
export const walletService = {
  getMyWallet: () =>
    axiosClient.get<ApiResponse<Wallet>>(`${BASE}/me`),

  topUp: (data: TopUpWalletRequest) =>
    axiosClient.post<ApiResponse<Wallet>>(`${BASE}/topup`, data),

  getTransactions: (params: { page?: number; size?: number } = {}) =>
    axiosClient.get<ApiResponse<PageResponse<WalletTransaction>>>(`${BASE}/transactions`, {
      params: { page: 0, size: 20, ...params },
    }),
}
