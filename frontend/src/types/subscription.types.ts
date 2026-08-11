// Mirrors subscription-service's SubscriptionResponse/CreateSubscriptionRequest exactly - this
// domain has no relationship to Product/ProductCategory (see product.types.ts) or a delivery
// slot/address concept; it identifies what's being delivered purely via MilkType, and has no
// delivery-address field of its own (delivery-service owns that separately).

export interface Subscription {
  id: string
  customerId: string
  milkType: MilkType
  quantity: number
  scheduleType: ScheduleType
  deliveryDays?: DeliveryDay[]
  startDate: string
  endDate?: string
  status: SubscriptionStatus
  pauseStart?: string
  pauseEnd?: string
  createdAt: string
  updatedAt?: string
}

export type MilkType = 'FULL_CREAM' | 'TONED' | 'DOUBLE_TONED' | 'SKIMMED'

export const MILK_TYPE_LABELS: Record<MilkType, string> = {
  FULL_CREAM: 'Full Cream',
  TONED: 'Toned',
  DOUBLE_TONED: 'Double Toned',
  SKIMMED: 'Skimmed',
}
export const MILK_TYPES: MilkType[] = ['FULL_CREAM', 'TONED', 'DOUBLE_TONED', 'SKIMMED']

export type ScheduleType = 'DAILY' | 'ALTERNATE_DAY' | 'WEEKLY'

export const SCHEDULE_TYPE_LABELS: Record<ScheduleType, string> = {
  DAILY: 'Daily',
  ALTERNATE_DAY: 'Alternate Day',
  WEEKLY: 'Weekly',
}
export const SCHEDULE_TYPES: ScheduleType[] = ['DAILY', 'ALTERNATE_DAY', 'WEEKLY']

export type DeliveryDay = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'
export const DELIVERY_DAYS: DeliveryDay[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'EXPIRED'

export const SUBSCRIPTION_STATUS_LABELS: Record<SubscriptionStatus, string> = {
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
}

export interface CreateSubscriptionRequest {
  // Required for FARM_MANAGER/SUPER_ADMIN (who the subscription is for); ignored by the backend
  // for a CUSTOMER caller, who can only ever create one for themselves.
  customerId?: string
  milkType: MilkType
  quantity: number
  scheduleType: ScheduleType
  deliveryDays?: DeliveryDay[]
  startDate: string
  endDate?: string
}

// Every field optional - null/omitted means "leave unchanged" (matches UpdateSubscriptionRequest
// on the backend). Note startDate and customerId are NOT editable here - the backend doesn't
// accept them on update.
export interface UpdateSubscriptionRequest {
  milkType?: MilkType
  quantity?: number
  scheduleType?: ScheduleType
  deliveryDays?: DeliveryDay[]
  endDate?: string
}

export interface PauseSubscriptionRequest {
  pauseEnd: string
}

export interface SubscriptionSearchParams {
  customerId?: string
  keyword?: string
  status?: SubscriptionStatus
  milkType?: MilkType
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
  sort?: string
}
