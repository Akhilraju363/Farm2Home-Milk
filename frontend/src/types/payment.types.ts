export interface Payment {
  id: string
  orderId: string
  customerId: string
  paymentReference: string
  amount: number
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  gatewayResponse?: string
  paidAt?: string
  createdAt: string
}

export interface Wallet {
  id: string
  customerId: string
  balance: number
}

export interface WalletTransaction {
  id: string
  walletId: string
  transactionType: 'CREDIT' | 'DEBIT'
  amount: number
  referenceId: string
  description: string
  createdAt: string
}

export type PaymentMethod = 'UPI' | 'RAZORPAY' | 'WALLET' | 'CASH'
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED'
