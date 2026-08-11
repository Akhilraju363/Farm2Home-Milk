// Sellable dairy catalog - distinct from InventoryItem (farm supplies like feed/medicine/
// equipment, see inventory.types.ts). Mirrors inventory-service's Product/ProductCategory domain.

export type ProductUnit = 'L' | 'ML' | 'KG' | 'G' | 'PACK' | 'DOZEN' | 'PIECE'

// Backend owns these values; this is a display label only, never submitted to the API (the raw
// enum value is always what's sent - see ProductFormDialog).
export const PRODUCT_UNIT_LABELS: Record<ProductUnit, string> = {
  L: 'Liter',
  ML: 'Milliliter',
  KG: 'Kilogram',
  G: 'Gram',
  PACK: 'Pack',
  DOZEN: 'Dozen',
  PIECE: 'Piece',
}

export const PRODUCT_UNITS: ProductUnit[] = ['L', 'ML', 'KG', 'G', 'PACK', 'DOZEN', 'PIECE']

// Response-derived only (see ProductMapper.computeDerivedFields on the backend) - never
// calculated client-side.
export type ProductStockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'

export const STOCK_STATUS_LABELS: Record<ProductStockStatus, string> = {
  IN_STOCK: 'In Stock',
  LOW_STOCK: 'Low Stock',
  OUT_OF_STOCK: 'Out of Stock',
}

export interface Product {
  id: string
  name: string
  description?: string | null
  categoryId?: string | null
  categoryName?: string | null
  price: number
  unit: ProductUnit
  stockQuantity: number
  minimumStockQuantity: number
  stockStatus: ProductStockStatus
  availability: boolean
  imageUrl?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductCategory {
  id: string
  name: string
  description?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateProductRequest {
  name: string
  description?: string
  categoryId?: string
  price: number
  unit: ProductUnit
  stockQuantity?: number
  minimumStockQuantity?: number
}

// Every field optional - null/omitted means "leave unchanged" (matches UpdateProductRequest on
// the backend, whose MapStruct mapping uses NullValuePropertyMappingStrategy.IGNORE).
export interface UpdateProductRequest {
  name?: string
  description?: string
  categoryId?: string
  price?: number
  unit?: ProductUnit
  stockQuantity?: number
  minimumStockQuantity?: number
  active?: boolean
}

export interface CreateProductCategoryRequest {
  name: string
  description?: string
}

export interface UpdateProductCategoryRequest {
  name?: string
  description?: string
  active?: boolean
}

export interface ProductSearchParams {
  keyword?: string
  active?: boolean
  categoryId?: string
  stockStatus?: ProductStockStatus
  available?: boolean
  page?: number
  size?: number
  sort?: string
}
