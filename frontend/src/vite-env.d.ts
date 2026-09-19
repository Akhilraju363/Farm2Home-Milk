/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GOOGLE_MAPS_API_KEY?: string
  readonly VITE_GOOGLE_CLIENT_ID?: string
  // Absolute origins for the two standalone Render backends (no runtime gateway in production -
  // see docs/FREE_DEPLOYMENT_ARCHITECTURE.md). Unset locally: axiosClient/authAxiosClient fall
  // back to the relative '/api/v1' path routed by the Vite dev proxy to the local api-gateway.
  readonly VITE_AUTH_API_URL?: string
  readonly VITE_BACKEND_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  // Google Maps' well-known global auth-failure callback - see TrackingMap.tsx.
  gm_authFailure?: () => void
}
