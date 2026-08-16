import axiosClient from './axiosClient'
import type { ApiResponse, PageResponse } from '../types/common.types'
import type { CreateReviewRequest, RatingSummary, Review, UpdateReviewRequest } from '../types/review.types'

const BASE = '/reviews'
const PRODUCTS_BASE = '/inventory/products'

export const reviewService = {
  create: (data: CreateReviewRequest) =>
    axiosClient.post<ApiResponse<Review>>(BASE, data),

  // No orderId filter on the backend - the caller's own review list is always small enough to
  // filter client-side for "does this order already have a review" (see OrderDetailsPage).
  findMy: (params?: { page?: number; size?: number }) =>
    axiosClient.get<ApiResponse<PageResponse<Review>>>(`${BASE}/my`, { params: { page: 0, size: 50, ...params } }),

  update: (id: string, data: UpdateReviewRequest) =>
    axiosClient.put<ApiResponse<Review>>(`${BASE}/${id}`, data),

  // Self-service delete for a CUSTOMER's own review, or moderation for SUPER_ADMIN/FARM_MANAGER -
  // same endpoint, the backend decides which applies from the caller's role/ownership.
  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  findByProduct: (productId: string, params?: { page?: number; size?: number }) =>
    axiosClient.get<ApiResponse<PageResponse<Review>>>(`${PRODUCTS_BASE}/${productId}/reviews`, {
      params: { page: 0, size: 10, ...params },
    }),

  ratingSummary: (productId: string) =>
    axiosClient.get<ApiResponse<RatingSummary>>(`${PRODUCTS_BASE}/${productId}/rating-summary`),
}
