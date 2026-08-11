import { Box, Paper, Typography, Chip, IconButton, Alert, Skeleton, Divider } from '@mui/material'
import { ArrowBack, LocalShipping, Person, AccessTime } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { TrackingMap } from '../../components/delivery/TrackingMap'
import { orderService } from '../../services/orderService'
import { deliveryService, deliveryPartnerService } from '../../services/deliveryService'
import { locationService } from '../../services/locationService'
import { statusColor } from '../../utils/formatters'
import { ASSIGNMENT_STATUS_LABELS } from '../../types/delivery.types'
import type { LocationFreshness } from '../../types/location.types'

const POLL_INTERVAL_MS = 15_000

const FRESHNESS_META: Record<LocationFreshness, { label: string; color: 'success' | 'warning' | 'default' }> = {
  LIVE: { label: 'Live', color: 'success' },
  RECENT: { label: 'Recently updated', color: 'warning' },
  STALE: { label: 'Signal delayed', color: 'default' },
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 3, borderRadius: 2 }}>
      <Typography variant="subtitle1" fontWeight={700} mb={1}>{title}</Typography>
      <Divider sx={{ mb: 2 }} />
      {children}
    </Paper>
  )
}

/** Customer-facing delivery tracking. Every value here comes from a real backend response -
 *  there is no ETA, no distance, and no route line, because the backend supports none of those
 *  (no routing provider integration exists). Only the delivery partner's own submitted GPS
 *  location is ever plotted - not a destination pin, since customer-service's address
 *  latitude/longitude columns exist in the schema but are never populated or exposed via the API. */
export function TrackingPage() {
  const { id: orderId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [now, setNow] = useState(Date.now())

  const { data: orderRes, isLoading: orderLoading } = useQuery({
    queryKey: ['orders', orderId],
    queryFn: () => orderService.getById(orderId!),
    enabled: Boolean(orderId),
    retry: false,
  })
  const order = orderRes?.data.data

  const { data: assignmentRes, isLoading: assignmentLoading } = useQuery({
    queryKey: ['delivery', 'by-order', orderId],
    queryFn: () => deliveryService.getByOrder(orderId!),
    enabled: Boolean(orderId),
    retry: false,
  })
  const assignment = assignmentRes?.data.data?.[0]
  const isActive = assignment?.status === 'OUT_FOR_DELIVERY'
  const hasEnded = assignment?.status === 'DELIVERED' || assignment?.status === 'FAILED'

  const { data: partnerRes } = useQuery({
    queryKey: ['delivery', 'partners', assignment?.deliveryPartnerId],
    queryFn: () => deliveryPartnerService.getById(assignment!.deliveryPartnerId),
    enabled: Boolean(assignment?.deliveryPartnerId),
  })
  const partner = partnerRes?.data.data

  // Polls only while the assignment is actively out for delivery - matches the backend, which
  // rejects submissions (and this page has nothing new to fetch) at any other status.
  const { data: locationRes } = useQuery({
    queryKey: ['delivery', 'location', assignment?.id],
    queryFn: () => locationService.getCurrent(assignment!.id),
    enabled: Boolean(assignment?.id) && isActive,
    refetchInterval: isActive ? POLL_INTERVAL_MS : false,
    refetchIntervalInBackground: false,
  })
  const location = locationRes?.data.data

  // Re-render every 15s so "Updated Ns ago" stays current without needing a fresh location fetch.
  useEffect(() => {
    if (!isActive) return
    const timer = setInterval(() => setNow(Date.now()), 15_000)
    return () => clearInterval(timer)
  }, [isActive])

  const secondsAgo = location ? Math.max(0, Math.round((now - new Date(location.recordedAt).getTime()) / 1000)) : null

  if (orderLoading || assignmentLoading) {
    return (
      <Box>
        <Skeleton width={160} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={320} sx={{ borderRadius: 2, mb: 2 }} />
        <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 2 }} />
      </Box>
    )
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
        <IconButton onClick={() => navigate(`/orders/${orderId}`)}><ArrowBack /></IconButton>
        <Box>
          <Typography variant="h5" fontWeight={700}>Delivery Tracking</Typography>
          {order && (
            <Typography variant="body2" color="text.secondary">
              Order {order.orderNumber}
              {assignment && (
                <>
                  {' • '}
                  <Chip label={ASSIGNMENT_STATUS_LABELS[assignment.status]} size="small" color={statusColor(assignment.status)} sx={{ ml: 0.5, verticalAlign: 'middle' }} />
                </>
              )}
            </Typography>
          )}
        </Box>
      </Box>

      {!assignment ? (
        <Alert severity="info">No delivery has been assigned to this order yet.</Alert>
      ) : hasEnded ? (
        <Alert severity={assignment.status === 'DELIVERED' ? 'success' : 'warning'}>
          Tracking has ended for this delivery — it is {ASSIGNMENT_STATUS_LABELS[assignment.status].toLowerCase()}.
        </Alert>
      ) : !isActive ? (
        <Alert severity="info">
          Your order has been assigned to a delivery partner. Live tracking will begin once it's out for delivery.
        </Alert>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 2 }}>
          <Box sx={{ flex: 2, minWidth: 0 }}>
            <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
              {location ? (
                <TrackingMap points={[{ latitude: location.latitude, longitude: location.longitude, label: partner?.name ?? 'Delivery partner' }]} height={360} />
              ) : (
                <Box sx={{ height: 360, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 1, color: 'text.secondary' }}>
                  <LocalShipping sx={{ fontSize: 40 }} />
                  <Typography variant="body2">Live tracking is not available for this delivery yet.</Typography>
                </Box>
              )}
            </Paper>
            {location && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1.5 }}>
                <Chip size="small" icon={<AccessTime fontSize="small" />} label={FRESHNESS_META[location.freshness].label} color={FRESHNESS_META[location.freshness].color} />
                <Typography variant="caption" color="text.secondary">
                  Updated {secondsAgo === 0 ? 'just now' : `${secondsAgo}s ago`}
                </Typography>
              </Box>
            )}
          </Box>

          <Box sx={{ flex: 1, minWidth: { md: 260 } }}>
            <Section title="Delivery Partner">
              {partner ? (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                    <Person color="action" />
                    <Typography variant="body2" fontWeight={600}>{partner.name}</Typography>
                  </Box>
                  {partner.vehicleType && (
                    <Typography variant="body2" color="text.secondary" mb={0.5}>Vehicle: {partner.vehicleType}</Typography>
                  )}
                  <Typography variant="body2" color="text.secondary">Mobile: {partner.mobile}</Typography>
                </>
              ) : (
                <Typography variant="body2" color="text.secondary">Partner details unavailable.</Typography>
              )}
            </Section>
          </Box>
        </Box>
      )}
    </Box>
  )
}
