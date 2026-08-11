export interface Order {
  id: string
  orderNumber: string
  customerId: string
  subscriptionId?: string
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
  milkType: MilkType
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

export interface CreateOrderItemRequest {
  milkType: MilkType
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
