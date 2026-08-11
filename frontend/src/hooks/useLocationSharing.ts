import { useCallback, useEffect, useRef, useState } from 'react'
import { locationService } from '../services/locationService'

// Matches the backend's own submission constraint (only accepted while OUT_FOR_DELIVERY) and the
// "every 10-30 seconds" guidance - not aggressive polling, not one-shot.
const SUBMIT_INTERVAL_MS = 15_000

export type SharingStatus = 'idle' | 'requesting' | 'active' | 'paused' | 'denied' | 'unavailable' | 'error'

/** Drives GPS submission for one delivery assignment. Never simulates a position - if the browser
 *  denies permission or geolocation isn't available, this surfaces that truthfully (see
 *  SharingStatus) rather than falling back to a fake coordinate. */
export function useLocationSharing(assignmentId: string) {
  const [status, setStatus] = useState<SharingStatus>('idle')
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const [errorMessage, setErrorMessage] = useState('')
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const clearTimer = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
  }

  const submitOnce = useCallback(() => {
    if (!navigator.geolocation) {
      setStatus('unavailable')
      clearTimer()
      return
    }
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          await locationService.submit(assignmentId, {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy ?? undefined,
            speed: position.coords.speed ?? undefined,
            heading: position.coords.heading ?? undefined,
          })
          setStatus('active')
          setLastUpdatedAt(new Date())
          setErrorMessage('')
        } catch (err: any) {
          setStatus('error')
          setErrorMessage(err.response?.data?.message ?? 'Could not send your location. Retrying shortly.')
        }
      },
      (geoError) => {
        if (geoError.code === geoError.PERMISSION_DENIED) {
          setStatus('denied')
          clearTimer()
        } else {
          setStatus('unavailable')
          setErrorMessage('Location is currently unavailable on this device.')
        }
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 0 },
    )
  }, [assignmentId])

  const start = useCallback(() => {
    setStatus('requesting')
    submitOnce()
    clearTimer()
    intervalRef.current = setInterval(submitOnce, SUBMIT_INTERVAL_MS)
  }, [submitOnce])

  const stop = useCallback(() => {
    clearTimer()
    setStatus('paused')
  }, [])

  useEffect(() => () => clearTimer(), [])

  return { status, lastUpdatedAt, errorMessage, start, stop }
}
