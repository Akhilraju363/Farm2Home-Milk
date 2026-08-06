export interface DashboardSummary {
  totalCustomers: number
  activeSubscriptions: number
  todaysOrders: number
  pendingOrders: number
  completedDeliveriesToday: number
  revenueToday: number
  revenueThisMonth: number
  lowStockProductsCount: number
  milkProductionToday: number
}

export type Granularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'

export interface RevenueTrendPoint {
  period: string
  revenue: number
}

export interface SubscriptionTrendPoint {
  period: string
  newSubscriptions: number
}

export interface CustomerGrowthPoint {
  period: string
  newCustomers: number
}

export interface TrendSeries<T> {
  granularity: Granularity
  from: string | null
  to: string | null
  points: T[]
}
