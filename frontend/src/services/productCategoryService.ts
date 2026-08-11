import axiosClient from './axiosClient'
import type { ApiResponse, PageResponse } from '../types/common.types'
import type { CreateProductCategoryRequest, ProductCategory, UpdateProductCategoryRequest } from '../types/product.types'

const BASE = '/inventory/product-categories'

export const productCategoryService = {
  getAll: (params: { activeOnly?: boolean; page?: number; size?: number } = {}) =>
    axiosClient.get<ApiResponse<PageResponse<ProductCategory>>>(BASE, {
      params: { activeOnly: true, page: 0, size: 100, ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<ProductCategory>>(`${BASE}/${id}`),

  create: (data: CreateProductCategoryRequest) =>
    axiosClient.post<ApiResponse<ProductCategory>>(BASE, data),

  update: (id: string, data: UpdateProductCategoryRequest) =>
    axiosClient.put<ApiResponse<ProductCategory>>(`${BASE}/${id}`, data),

  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),
}
