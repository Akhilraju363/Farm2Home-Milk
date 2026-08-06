import axiosClient from './axiosClient'
import type { ApiResponse } from '../types/common.types'
import type {
  DashboardSummary, TrendSeries, RevenueTrendPoint, SubscriptionTrendPoint, CustomerGrowthPoint,
} from '../types/dashboard.types'

export const dashboardService = {
  getSummary: () =>
    axiosClient.get<ApiResponse<DashboardSummary>>('/dashboard/summary'),

  getRevenueTrend: (dateFrom: string, dateTo: string) =>
    axiosClient.get<ApiResponse<TrendSeries<RevenueTrendPoint>>>('/payments/analytics/revenue-trend', {
      params: { granularity: 'DAILY', dateFrom, dateTo },
    }),

  getSubscriptionTrend: (dateFrom: string, dateTo: string) =>
    axiosClient.get<ApiResponse<TrendSeries<SubscriptionTrendPoint>>>('/subscriptions/analytics/subscription-trend', {
      params: { granularity: 'DAILY', dateFrom, dateTo },
    }),

  getCustomerGrowthTrend: (dateFrom: string, dateTo: string) =>
    axiosClient.get<ApiResponse<TrendSeries<CustomerGrowthPoint>>>('/customers/analytics/growth-trend', {
      params: { granularity: 'DAILY', dateFrom, dateTo },
    }),
}
