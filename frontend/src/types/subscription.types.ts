export interface Subscription {
  id: string
  customerId: string
  milkType: MilkType
  quantity: number
  scheduleType: ScheduleType
  deliveryDays?: string
  startDate: string
  endDate?: string
  status: SubscriptionStatus
  pauseStart?: string
  pauseEnd?: string
}

export type MilkType = 'FULL_CREAM' | 'TONED' | 'DOUBLE_TONED' | 'SKIMMED'
export type ScheduleType = 'DAILY' | 'ALTERNATE_DAY' | 'WEEKLY'
export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'EXPIRED'
