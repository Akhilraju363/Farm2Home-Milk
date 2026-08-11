import { Box, Paper, Typography, List, ListItemButton, ListItemText, Chip } from '@mui/material'
import { LocalShipping, AccessTime } from '@mui/icons-material'
import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { TrackingMap } from '../../components/delivery/TrackingMap'
import { deliveryService } from '../../services/deliveryService'
import { locationService } from '../../services/locationService'
import { formatDateTime } from '../../utils/formatters'
import type { Assignment } from '../../types/delivery.types'
import type { LocationFreshness } from '../../types/location.types'

const POLL_INTERVAL_MS = 15_000

const FRESHNESS_META: Record<LocationFreshness, { label: string; color: 'success' | 'warning' | 'default' }> = {
  LIVE: { label: 'Live', color: 'success' },
  RECENT: { label: 'Recent', color: 'warning' },
  STALE: { label: 'Stale', color: 'default' },
}

/** Admin/ops view of every currently-active (OUT_FOR_DELIVERY) delivery. Deliberately does not
 *  group partners into Active/Delayed/Offline buckets - no per-partner online/offline concept
 *  exists in the backend, only per-assignment location freshness (derived from the same
 *  recordedAt every other tracking screen uses), so that's the only distinction shown. */
export function AdminTrackingPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['delivery', 'search', 'OUT_FOR_DELIVERY'],
    queryFn: () => deliveryService.search({ status: 'OUT_FOR_DELIVERY', size: 50, sort: 'assignedAt,desc' }),
    refetchInterval: POLL_INTERVAL_MS,
  })
  const assignments = data?.data.data.content ?? []

  useEffect(() => {
    if (!selectedId && assignments.length > 0) setSelectedId(assignments[0].id)
    if (selectedId && !assignments.some((a) => a.id === selectedId)) setSelectedId(assignments[0]?.id ?? null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assignments])

  const { data: locationRes } = useQuery({
    queryKey: ['delivery', 'location', selectedId],
    queryFn: () => locationService.getCurrent(selectedId!),
    enabled: Boolean(selectedId),
    refetchInterval: POLL_INTERVAL_MS,
  })
  const location = locationRes?.data.data
  const selected = assignments.find((a) => a.id === selectedId) as Assignment | undefined

  return (
    <Box>
      <PageHeader title="Delivery Tracking" subtitle="Live location for deliveries currently out for delivery." />

      {!isLoading && assignments.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <LocalShipping sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700}>No deliveries are currently out for delivery.</Typography>
        </Paper>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 2 }}>
          <Paper variant="outlined" sx={{ borderRadius: 2, width: { md: 320 }, flexShrink: 0, maxHeight: 480, overflowY: 'auto' }}>
            <List disablePadding>
              {assignments.map((a) => (
                <ListItemButton key={a.id} selected={a.id === selectedId} onClick={() => setSelectedId(a.id)} divider>
                  <ListItemText
                    primary={a.deliveryPartnerName}
                    secondary={`Route ${a.routeCode} • Order ${a.orderId.slice(0, 8)}…`}
                  />
                </ListItemButton>
              ))}
            </List>
          </Paper>

          <Box sx={{ flex: 1, minWidth: 0 }}>
            {selected && (
              <>
                <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
                  {location ? (
                    <TrackingMap points={[{ latitude: location.latitude, longitude: location.longitude, label: selected.deliveryPartnerName }]} height={420} />
                  ) : (
                    <Box sx={{ height: 420, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 1, color: 'text.secondary' }}>
                      <LocalShipping sx={{ fontSize: 40 }} />
                      <Typography variant="body2">This partner hasn't started sharing their location yet.</Typography>
                    </Box>
                  )}
                </Paper>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 1.5, flexWrap: 'wrap' }}>
                  {location && (
                    <Chip size="small" icon={<AccessTime fontSize="small" />} label={FRESHNESS_META[location.freshness].label} color={FRESHNESS_META[location.freshness].color} />
                  )}
                  <Typography variant="body2" color="text.secondary">
                    {selected.deliveryPartnerName} • {selected.deliveryPartnerMobile} • Route {selected.routeCode}
                  </Typography>
                  {location && (
                    <Typography variant="caption" color="text.secondary">
                      Last update: {formatDateTime(location.recordedAt)}
                    </Typography>
                  )}
                </Box>
              </>
            )}
          </Box>
        </Box>
      )}
    </Box>
  )
}
