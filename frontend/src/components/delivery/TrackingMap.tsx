import { useEffect, useState } from 'react'
import { GoogleMap, InfoWindow, Marker, useJsApiLoader } from '@react-google-maps/api'
import { Box, Typography } from '@mui/material'
import { LocationOff, ErrorOutline } from '@mui/icons-material'
import { GOOGLE_MAPS_API_KEY, isGoogleMapsConfigured } from '../../utils/googleMapsConfig'

interface MapPoint {
  latitude: number
  longitude: number
  label: string
}

interface Props {
  points: MapPoint[]
  height?: number | string
}

function MapMessage({ height, icon, message }: { height: number | string; icon: React.ReactNode; message: string }) {
  return (
    <Box sx={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 1, color: 'text.secondary', bgcolor: 'action.hover' }}>
      {icon}
      <Typography variant="body2">{message}</Typography>
    </Box>
  )
}

/** Real backend coordinates only - every caller passes points sourced from
 *  GET /delivery/assignments/{id}/location or a customer's saved address, never a
 *  hardcoded/simulated position. Renders via the Google Maps JavaScript SDK, keyed by
 *  VITE_GOOGLE_MAPS_API_KEY (see googleMapsConfig.ts) - the key never reaches any Farm2Home
 *  backend service, only Google's own loader. */
export function TrackingMap({ points, height = 320 }: Props) {
  const [activeMarker, setActiveMarker] = useState<number | null>(null)
  const [authFailed, setAuthFailed] = useState(false)

  const { isLoaded, loadError } = useJsApiLoader({
    id: 'farm2home-google-maps',
    googleMapsApiKey: GOOGLE_MAPS_API_KEY ?? '',
  })

  // A missing/invalid/restricted key is a runtime auth failure inside Google's own loaded script,
  // not a script-load failure - useJsApiLoader's loadError never fires for it. Google instead
  // calls this well-known global callback and paints its own "Oops!" overlay directly into the
  // map div. Registering it lets us show our own message in that same area instead.
  useEffect(() => {
    window.gm_authFailure = () => setAuthFailed(true)
    return () => { delete window.gm_authFailure }
  }, [])

  if (points.length === 0) return null

  if (!isGoogleMapsConfigured()) {
    return <MapMessage height={height} icon={<LocationOff sx={{ fontSize: 40 }} />} message="Google Maps API key is not configured." />
  }

  if (loadError || authFailed) {
    return <MapMessage height={height} icon={<ErrorOutline sx={{ fontSize: 40 }} />} message="Unable to load Google Maps. Please try again later." />
  }

  if (!isLoaded) {
    return <MapMessage height={height} icon={<LocationOff sx={{ fontSize: 40 }} />} message="Loading map…" />
  }

  const center = { lat: points[0].latitude, lng: points[0].longitude }

  return (
    <Box sx={{ height, '& > div': { height: '100%', width: '100%' } }}>
      <GoogleMap mapContainerStyle={{ height: '100%', width: '100%' }} center={center} zoom={14} options={{ scrollwheel: false }}>
        {points.map((p, i) => (
          <Marker key={i} position={{ lat: p.latitude, lng: p.longitude }} onClick={() => setActiveMarker(i)}>
            {activeMarker === i && (
              <InfoWindow onCloseClick={() => setActiveMarker(null)}>
                <Typography variant="body2">{p.label}</Typography>
              </InfoWindow>
            )}
          </Marker>
        ))}
      </GoogleMap>
    </Box>
  )
}
