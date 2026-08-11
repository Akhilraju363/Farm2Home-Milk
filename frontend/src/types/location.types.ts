export type LocationFreshness = 'LIVE' | 'RECENT' | 'STALE'

export interface DeliveryLocation {
  deliveryAssignmentId: string
  latitude: number
  longitude: number
  accuracy?: number
  speed?: number
  heading?: number
  recordedAt: string
  freshness: LocationFreshness
}

export interface SubmitLocationRequest {
  latitude: number
  longitude: number
  accuracy?: number
  speed?: number
  heading?: number
}
