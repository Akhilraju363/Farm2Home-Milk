import axiosClient from './axiosClient'
import type {
  AddCartItemRequest, CartResponse, UpdateCartItemRequest,
} from '../types/cart.types'
import type { ApiResponse } from '../types/common.types'

const BASE = '/cart'

// Self-scoped only, matches CartController exactly - there is no admin/on-behalf-of variant
// (see order-service's CartController: every method resolves the acting customer from
// @AuthenticationPrincipal, never from a request/path parameter).
export const cartService = {
  getCart: () =>
    axiosClient.get<ApiResponse<CartResponse>>(BASE),

  addItem: (data: AddCartItemRequest) =>
    axiosClient.post<ApiResponse<CartResponse>>(`${BASE}/items`, data),

  updateItem: (itemId: string, data: UpdateCartItemRequest) =>
    axiosClient.put<ApiResponse<CartResponse>>(`${BASE}/items/${itemId}`, data),

  removeItem: (itemId: string) =>
    axiosClient.delete<ApiResponse<CartResponse>>(`${BASE}/items/${itemId}`),

  clear: () =>
    axiosClient.delete<ApiResponse<void>>(BASE),
}
