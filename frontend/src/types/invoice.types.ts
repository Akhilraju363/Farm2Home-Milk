export interface InvoiceLineItem {
  milkType: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface InvoiceAddress {
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  pincode?: string
}

export interface InvoicePaymentInfo {
  paymentReference?: string
  paymentMethod?: string
  paymentStatus?: string
  paidAt?: string
}

// Composed by invoice-service at read time from order/payment/customer data - fields from a
// downstream service are undefined (not fabricated) if that service didn't respond in time.
export interface Invoice {
  id: string
  invoiceNumber: string
  issueDate: string
  orderId: string
  orderNumber?: string
  orderDate?: string
  // order-service's real order status (PENDING/ASSIGNED/OUT_FOR_DELIVERY/DELIVERED/CANCELLED) -
  // there is no separate invoice status, the backend has none.
  orderStatus?: string
  items: InvoiceLineItem[]
  customerId: string
  customerName?: string
  customerMobile?: string
  customerEmail?: string
  billingAddress?: InvoiceAddress
  // No tax/deliveryCharge/discount fields - none exist anywhere in the backend, so subtotal and
  // totalAmount are always equal.
  subtotal: number
  totalAmount: number
  payment?: InvoicePaymentInfo
  createdAt: string
}

export interface InvoiceSummary {
  id: string
  invoiceNumber: string
  issueDate: string
  orderId: string
  orderNumber?: string
  orderStatus?: string
  customerId: string
  customerName?: string
  totalAmount: number
}

export interface InvoiceSearchParams {
  keyword?: string
  customerId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}
