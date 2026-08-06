import { Box, Paper, Typography, List, ListItemButton, ListItemAvatar, Avatar, ListItemText, Chip, CircularProgress } from '@mui/material'
import {
  ShoppingCart, LocalShipping, CheckCircle, AccessTime, Payment, ErrorOutline,
  Autorenew, PauseCircle, PlayCircle, Cancel, PersonAdd, Notifications as NotificationsIcon,
  DoneAll,
} from '@mui/icons-material'
import type { ReactElement } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { PageHeader } from '../../components/common/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import { notificationService } from '../../services/notificationService'
import type { NotificationPriority, NotificationSummaryItem } from '../../types/notification.types'

const EVENT_META: Record<string, { icon: ReactElement; label: string; color: string }> = {
  ORDER_CREATED: { icon: <ShoppingCart />, label: 'Order Confirmed', color: 'primary.main' },
  DELIVERY_ASSIGNED: { icon: <LocalShipping />, label: 'Delivery Assigned', color: 'info.main' },
  DELIVERY_OUT_FOR_DELIVERY: { icon: <LocalShipping />, label: 'Out for Delivery', color: 'info.main' },
  DELIVERY_COMPLETED: { icon: <CheckCircle />, label: 'Delivered', color: 'success.main' },
  DELIVERY_DELAYED: { icon: <AccessTime />, label: 'Delivery Delayed', color: 'warning.main' },
  PAYMENT_SUCCESS: { icon: <Payment />, label: 'Payment Successful', color: 'success.main' },
  PAYMENT_FAILED: { icon: <ErrorOutline />, label: 'Payment Failed', color: 'error.main' },
  SUBSCRIPTION_CREATED: { icon: <Autorenew />, label: 'Subscription Started', color: 'success.main' },
  SUBSCRIPTION_PAUSED: { icon: <PauseCircle />, label: 'Subscription Paused', color: 'warning.main' },
  SUBSCRIPTION_RESUMED: { icon: <PlayCircle />, label: 'Subscription Resumed', color: 'success.main' },
  SUBSCRIPTION_CANCELLED: { icon: <Cancel />, label: 'Subscription Cancelled', color: 'error.main' },
  CUSTOMER_CREATED: { icon: <PersonAdd />, label: 'Welcome to Farm2Home', color: 'primary.main' },
}

const PRIORITY_COLOR: Record<NotificationPriority, 'error' | 'warning' | 'default'> = {
  HIGH: 'error', MEDIUM: 'warning', LOW: 'default',
}

// Falls back gracefully for any eventType not in EVENT_META above (e.g. a new event type added
// to the backend later) instead of rendering a blank/broken row.
function eventMeta(eventType: string) {
  return EVENT_META[eventType] ?? {
    icon: <NotificationsIcon />,
    label: eventType.replace(/_/g, ' ').replace(/\w\S*/g, (w) => w[0].toUpperCase() + w.slice(1).toLowerCase()),
    color: 'text.secondary',
  }
}

function dayGroupLabel(date: Dayjs): string {
  const today = dayjs().startOf('day')
  const target = date.startOf('day')
  if (target.isSame(today)) return 'Today'
  if (target.isSame(today.subtract(1, 'day'))) return 'Yesterday'
  return date.format('DD MMM YYYY')
}

function groupByDay(items: NotificationSummaryItem[]): Array<[string, NotificationSummaryItem[]]> {
  const groups: Array<[string, NotificationSummaryItem[]]> = []
  for (const item of items) {
    const label = dayGroupLabel(dayjs(item.createdAt))
    const lastGroup = groups[groups.length - 1]
    if (lastGroup && lastGroup[0] === label) {
      lastGroup[1].push(item)
    } else {
      groups.push([label, [item]])
    }
  }
  return groups
}

export function NotificationsPage() {
  const { isAdmin } = useAuth()
  const admin = isAdmin()
  const queryClient = useQueryClient()
  // Admin reaches this page via the sidebar's admin-only entry and sees every recipient's
  // recent activity (read-only - marking someone else's notification "read" on their behalf
  // isn't a real action, and the backend's markAsRead ownership check would 404 it anyway).
  // Everyone else reaches it via the bell's "View all" and sees only their own, interactively.
  const { data, isLoading } = useQuery({
    queryKey: ['notifications', admin ? 'summary' : 'mine', 'full'],
    queryFn: () => (admin ? notificationService.getSummary(100) : notificationService.getMine(50)),
    refetchInterval: 60_000,
  })
  const items = data?.data.data ?? []
  const groups = groupByDay(items)
  const unreadCount = items.filter((i) => !i.read).length

  // Both mutations invalidate every notifications query (bell dropdown + this page) rather than
  // just this page's own key, so read state stays consistent wherever it's shown.
  const invalidateAll = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })

  const markOne = useMutation({
    mutationFn: (id: string) => notificationService.markAsRead(id),
    onSuccess: invalidateAll,
  })
  const markAll = useMutation({
    mutationFn: () => notificationService.markAllAsRead(),
    onSuccess: invalidateAll,
  })

  return (
    <Box>
      <PageHeader
        title="Notifications"
        subtitle={admin ? 'Recent activity across every customer' : 'Updates about your orders, subscriptions, and payments'}
        action={!admin && unreadCount > 0 ? {
          label: 'Mark all as read',
          icon: <DoneAll />,
          onClick: () => markAll.mutate(),
        } : undefined}
      />

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress />
        </Box>
      ) : items.length === 0 ? (
        <Paper sx={{ p: 6, textAlign: 'center' }}>
          <NotificationsIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
          <Typography color="text.secondary">Nothing here yet</Typography>
        </Paper>
      ) : (
        groups.map(([label, groupItems]) => (
          <Box key={label} sx={{ mb: 3 }}>
            <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ mb: 1, px: 0.5 }}>
              {label}
            </Typography>
            <Paper variant="outlined">
              <List disablePadding>
                {groupItems.map((item, idx) => {
                  const meta = eventMeta(item.eventType)
                  return (
                    <ListItemButton
                      key={item.id}
                      divider={idx < groupItems.length - 1}
                      disableRipple={admin}
                      onClick={() => !admin && !item.read && markOne.mutate(item.id)}
                      sx={{ py: 1.5, bgcolor: item.read ? 'transparent' : 'action.hover', cursor: admin ? 'default' : 'pointer' }}
                    >
                      <ListItemAvatar>
                        <Avatar sx={{ bgcolor: meta.color, width: 36, height: 36 }}>
                          {meta.icon}
                        </Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography component="span" fontWeight={item.read ? 400 : 700}>
                              {item.subject || meta.label}
                            </Typography>
                            {item.priority === 'HIGH' && (
                              <Chip label="High" size="small" color={PRIORITY_COLOR[item.priority]} sx={{ height: 18, fontSize: 10 }} />
                            )}
                            {!item.read && (
                              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'primary.main' }} />
                            )}
                          </Box>
                        }
                        secondary={
                          <>
                            {admin && `To: ${item.recipient} • `}
                            {item.message}
                            <Typography component="span" variant="caption" color="text.disabled" sx={{ display: 'block', mt: 0.25 }}>
                              {dayjs(item.createdAt).format('hh:mm A')}
                            </Typography>
                          </>
                        }
                        secondaryTypographyProps={{ component: 'div' }}
                      />
                    </ListItemButton>
                  )
                })}
              </List>
            </Paper>
          </Box>
        ))
      )}
    </Box>
  )
}
