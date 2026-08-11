import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import { Box } from '@mui/material'

// Vite bundles Leaflet's default marker icon URLs incorrectly (a well-known upstream issue -
// Leaflet's CSS references relative image paths that don't survive bundling) - this replaces the
// default icon with the same images resolved through Vite's own asset pipeline instead.
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

const defaultIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
})

interface MapPoint {
  latitude: number
  longitude: number
  label: string
}

interface Props {
  points: MapPoint[]
  height?: number | string
}

/** Real backend coordinates only - every caller passes points sourced from
 *  GET /delivery/assignments/{id}/location or a customer's saved address, never a
 *  hardcoded/simulated position. Uses OpenStreetMap tiles (free, no API key) via Leaflet. */
export function TrackingMap({ points, height = 320 }: Props) {
  if (points.length === 0) return null
  const center: [number, number] = [points[0].latitude, points[0].longitude]

  return (
    <Box sx={{ height, borderRadius: 2, overflow: 'hidden', '& .leaflet-container': { height: '100%', width: '100%' } }}>
      <MapContainer center={center} zoom={14} scrollWheelZoom={false}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {points.map((p, i) => (
          <Marker key={i} position={[p.latitude, p.longitude]} icon={defaultIcon}>
            <Popup>{p.label}</Popup>
          </Marker>
        ))}
      </MapContainer>
    </Box>
  )
}
