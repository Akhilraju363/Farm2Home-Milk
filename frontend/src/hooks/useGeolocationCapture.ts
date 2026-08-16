import { useState } from 'react'

interface Coords {
  latitude: number
  longitude: number
}

/** Real device GPS only, via the browser's own Geolocation API - never derived from a typed
 *  city/district/pincode. Used wherever a customer address is created/edited (RegisterPage's
 *  Address step, the Shop delivery-address dialog) so DeliveryAvailability has something real to
 *  compute against; declining/skipping capture is fine, the address just stays coordinate-less
 *  until captured. */
export function useGeolocationCapture() {
  const [coords, setCoords] = useState<Coords | null>(null)
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState('')

  const capture = () => {
    setError('')
    if (!navigator.geolocation) {
      setError('Location is not supported by this browser.')
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude })
        setLocating(false)
      },
      () => {
        setError("Couldn't access your location. You can still save this address and set it later.")
        setLocating(false)
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    )
  }

  const reset = () => { setCoords(null); setError(''); setLocating(false) }

  return { coords, locating, error, capture, reset }
}
