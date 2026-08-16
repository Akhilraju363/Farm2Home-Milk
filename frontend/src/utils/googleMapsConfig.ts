// Read only via Vite's import.meta.env - never hardcode a key here. The real key lives in the
// developer's own untracked .env.local (see .env.example and docs/google-maps-setup.md).
export const GOOGLE_MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined

export function isGoogleMapsConfigured(): boolean {
  return Boolean(GOOGLE_MAPS_API_KEY && GOOGLE_MAPS_API_KEY.trim().length > 0)
}
