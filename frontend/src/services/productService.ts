import axiosClient from './axiosClient'
import type { ApiResponse, PageResponse } from '../types/common.types'
import type { CreateProductRequest, Product, ProductSearchParams, UpdateProductRequest } from '../types/product.types'

const BASE = '/inventory/products'

export const productService = {
  // /search (not the plain list endpoint) is used for every list fetch - it's the only endpoint
  // that supports the full filter set (keyword/category/stockStatus/available/active) the Product
  // Management screen needs, so the list, card view, and export all stay on one code path.
  search: (params: ProductSearchParams) =>
    axiosClient.get<ApiResponse<PageResponse<Product>>>(`${BASE}/search`, {
      params: { page: 0, size: 20, sort: 'name', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Product>>(`${BASE}/${id}`),

  create: (data: CreateProductRequest) =>
    axiosClient.post<ApiResponse<Product>>(BASE, data),

  update: (id: string, data: UpdateProductRequest) =>
    axiosClient.put<ApiResponse<Product>>(`${BASE}/${id}`, data),

  // Soft-deletes the product outright (permanently excluded from every future query - there's no
  // undelete API). The card/table "Deactivate" action deliberately uses update({active:false})
  // instead, since it's the reversible operation the UI actually offers - this is kept for
  // completeness/future use, not currently wired into the list screen.
  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${BASE}/${id}`),

  uploadImage: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    // axiosClient sets a default 'Content-Type: application/json' header - overriding it to
    // `undefined` (not the literal string 'multipart/form-data') lets axios auto-detect the
    // FormData body and generate the correct header including the required boundary itself.
    return axiosClient.post<ApiResponse<Product>>(`${BASE}/${id}/image`, formData, {
      headers: { 'Content-Type': undefined },
    })
  },

  export: async (params: ProductSearchParams & { format: 'CSV' | 'EXCEL' | 'PDF' }) => {
    const res = await axiosClient.get(`${BASE}/export`, { params, responseType: 'blob' })
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `products.${params.format.toLowerCase()}`
    return { blob: res.data as Blob, filename }
  },
}
