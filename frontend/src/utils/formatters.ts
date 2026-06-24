import dayjs from 'dayjs'

export const formatDate = (date?: string | null): string => {
  if (!date) return '—'
  return dayjs(date).format('DD MMM YYYY')
}

export const formatDateTime = (date?: string | null): string => {
  if (!date) return '—'
  return dayjs(date).format('DD MMM YYYY, hh:mm A')
}

export const formatCurrency = (amount?: number | null): string => {
  if (amount == null) return '—'
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(amount)
}

export const formatNumber = (n?: number | null, decimals = 2): string => {
  if (n == null) return '—'
  return n.toFixed(decimals)
}

export const statusColor = (status: string): 'success' | 'error' | 'warning' | 'info' | 'default' => {
  const map: Record<string, 'success' | 'error' | 'warning' | 'info' | 'default'> = {
    ACTIVE: 'success', DELIVERED: 'success', SUCCESS: 'success', SENT: 'success',
    INACTIVE: 'default', CANCELLED: 'error', FAILED: 'error', SUSPENDED: 'error',
    PENDING: 'warning', PAUSED: 'warning', OUT_FOR_DELIVERY: 'info',
    ASSIGNED: 'info', EXPIRED: 'default', REFUNDED: 'info',
  }
  return map[status] ?? 'default'
}
