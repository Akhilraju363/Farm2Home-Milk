export type NotificationChannel = 'SMS' | 'EMAIL' | 'PUSH'
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED'
export type NotificationType = 'ORDER' | 'PAYMENT' | 'DELIVERY' | 'SUBSCRIPTION' | 'ACCOUNT' | 'OTHER'
export type NotificationPriority = 'HIGH' | 'MEDIUM' | 'LOW'

export interface NotificationSummaryItem {
  id: string
  channel: NotificationChannel
  recipient: string
  eventType: string
  type: NotificationType
  priority: NotificationPriority
  subject?: string
  message: string
  status: NotificationStatus
  read: boolean
  createdAt: string
  sentAt?: string
}
