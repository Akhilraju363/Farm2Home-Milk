export interface Order {
  id: string
  orderNumber: string
  customerId: string
  subscriptionId?: string
  orderDate: string
  orderType: OrderType
  totalAmount: number
  status: OrderStatus
  items: OrderItem[]
}

export interface OrderItem {
  id: string
  orderId: string
  milkType: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export type OrderType = 'SUBSCRIPTION' | 'ONE_TIME'
export type OrderStatus =
  | 'PENDING'
  | 'ASSIGNED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
