export interface Order {
  id: string
  orderNumber: string
  customerId: string
  subscriptionId?: string
  // Automatically selected at order-creation time from the customer's delivery address (see
  // customer-service's DeliveryRouteSelectionServiceImpl, the sole authoritative implementation)
  // - never settable by the client. Null if no active route covered the address, or the order
  // predates this feature - show "Route not assigned", not an error.
  deliveryRouteId?: string | null
  orderDate: string
  orderType: OrderType
  status: OrderStatus
  totalAmount: number
  notes?: string
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

export interface OrderItem {
  id: string
  // Exactly one of milkType/productId is ever set - milkType is the legacy/subscription shape,
  // productId is a real inventory-service catalog product (see ShopProductDetailsPage/
  // BuyNowDialog). productName is snapshotted at order time by the backend.
  milkType?: MilkType | null
  productId?: string | null
  productName?: string | null
  quantity: number
  unitPrice: number
  totalPrice: number
}

// Matches order-service's OrderType enum exactly (SUBSCRIPTION / ONE_TIME) - NOT "MANUAL", which
// is what OrderResponse's own stale @Schema doc comment claims.
export type OrderType = 'SUBSCRIPTION' | 'ONE_TIME'
export const ORDER_TYPE_LABELS: Record<OrderType, string> = {
  SUBSCRIPTION: 'Subscription',
  ONE_TIME: 'One-Time',
}

export type OrderStatus = 'PENDING' | 'ASSIGNED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'CANCELLED'
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  ASSIGNED: 'Assigned',
  OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
}

// Matches OrderStatus.canTransitionTo() in the backend exactly - drives which action buttons
// render on Order Details. DELIVERED/CANCELLED are terminal (no entry here).
export const ORDER_STATUS_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['OUT_FOR_DELIVERY', 'CANCELLED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'CANCELLED'],
  DELIVERED: [],
  CANCELLED: [],
}

// Matches order-service's MilkType enum exactly (same set as subscription.types.ts - Order and
// Subscription each define their own copy of this enum server-side, not a shared Product type).
export type MilkType = 'FULL_CREAM' | 'TONED' | 'DOUBLE_TONED' | 'SKIMMED'
export const MILK_TYPES: MilkType[] = ['FULL_CREAM', 'TONED', 'DOUBLE_TONED', 'SKIMMED']
export const MILK_TYPE_LABELS: Record<MilkType, string> = {
  FULL_CREAM: 'Full Cream',
  TONED: 'Toned',
  DOUBLE_TONED: 'Double Toned',
  SKIMMED: 'Skimmed',
}

// Every list/table rendering an OrderItem's "what is this" label goes through here, so a
// product-based item (milkType null, see OrderItem's comment) never hits a bare index lookup
// into MILK_TYPE_LABELS and instead shows its own snapshotted productName.
export function orderItemLabel(item: Pick<OrderItem, 'milkType' | 'productName'>): string {
  return item.milkType ? MILK_TYPE_LABELS[item.milkType] : (item.productName ?? 'Product')
}

export interface CreateOrderItemRequest {
  // Specify exactly one - see OrderItem's comment above.
  milkType?: MilkType
  productId?: string
  quantity: number
}

export interface CreateOrderRequest {
  customerId?: string // admin only - ignored by the backend for a CUSTOMER caller
  orderDate: string
  notes?: string
  items: CreateOrderItemRequest[]
}

export interface UpdateOrderStatusRequest {
  status: OrderStatus
  notes?: string
}

// Matches order-service's OrderSummaryResponse exactly (GET /orders/summary).
export interface OrderSummary {
  todaysOrders: number
  pendingOrders: number
}

export interface OrderSearchParams {
  customerId?: string // admin only
  keyword?: string
  dateFrom?: string
  dateTo?: string
  status?: OrderStatus
  milkType?: MilkType
  page?: number
  size?: number
  sort?: string
}
