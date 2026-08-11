import { Box, Button, Typography, Chip } from '@mui/material'
import { MyLocation, LocationOff, GpsFixed, GpsOff, ErrorOutline } from '@mui/icons-material'
import { useLocationSharing } from '../../hooks/useLocationSharing'
import { formatDateTime } from '../../utils/formatters'

const STATUS_META: Record<string, { label: string; color: 'default' | 'success' | 'warning' | 'error'; icon: React.ReactNode }> = {
  idle:       { label: 'Location sharing is off',        color: 'default', icon: <LocationOff fontSize="small" /> },
  requesting: { label: 'Requesting location permission…', color: 'warning', icon: <GpsFixed fontSize="small" /> },
  active:     { label: 'Location sharing is active',      color: 'success', icon: <GpsFixed fontSize="small" /> },
  paused:     { label: 'Location sharing is paused',      color: 'default', icon: <GpsOff fontSize="small" /> },
  denied:     { label: 'Location permission denied',      color: 'error',   icon: <ErrorOutline fontSize="small" /> },
  unavailable:{ label: 'Location unavailable on this device', color: 'error', icon: <ErrorOutline fontSize="small" /> },
  error:      { label: 'Could not send location',         color: 'error',   icon: <ErrorOutline fontSize="small" /> },
}

/** Embedded in a delivery card only while the assignment is OUT_FOR_DELIVERY (see
 *  MyDeliveriesPage) - the backend rejects location submission for any other status, so there's
 *  nothing useful to show otherwise. Never displays raw lat/lng - only a plain-language status and
 *  a relative "last updated" time, per the spec's "don't expose raw GPS coordinates unnecessarily". */
export function LocationSharingControl({ assignmentId }: { assignmentId: string }) {
  const { status, lastUpdatedAt, errorMessage, start, stop } = useLocationSharing(assignmentId)
  const meta = STATUS_META[status]
  const isSharing = status === 'active' || status === 'requesting'

  return (
    <Box sx={{ mt: 1.5, p: 1.5, borderRadius: 1.5, bgcolor: 'action.hover' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Chip icon={meta.icon as any} label={meta.label} size="small" color={meta.color} variant="outlined" />
        {isSharing ? (
          <Button size="small" variant="outlined" color="inherit" startIcon={<MyLocation fontSize="small" />} onClick={stop}>
            Stop Sharing
          </Button>
        ) : (
          <Button size="small" variant="contained" startIcon={<MyLocation fontSize="small" />} onClick={start}>
            Share My Location
          </Button>
        )}
      </Box>
      {lastUpdatedAt && (
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.75 }}>
          Last updated: {formatDateTime(lastUpdatedAt.toISOString())}
        </Typography>
      )}
      {errorMessage && (
        <Typography variant="caption" color="error" display="block" sx={{ mt: 0.5 }}>
          {errorMessage}
        </Typography>
      )}
    </Box>
  )
}
