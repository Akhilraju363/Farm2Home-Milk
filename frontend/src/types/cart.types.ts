// Mirrors order-service's Cart domain exactly (CartServiceImpl.toResponse/toItemResponse) - one
// cart per customer, product-only (no milkType), no price/name snapshot stored server-side: every
// field below except id/productId/quantity is resolved live from inventory-service on each read.

export interface CartItemResponse {
  id: string
  productId: string
  productName?: string | null
  imageUrl?: string | null
  quantity: number
  unit?: string | null
  unitPrice?: number | null
  subtotal?: number | null
  available: boolean
  unavailableReason?: string | null
}

export interface CartResponse {
  cartId: string
  items: CartItemResponse[]
  subtotal: number
  itemCount: number
}

export interface AddCartItemRequest {
  productId: string
  quantity: number
}

export interface UpdateCartItemRequest {
  quantity: number
}

export interface CheckoutRequest {
  orderDate: string
}
