/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GOOGLE_MAPS_API_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  // Google Maps' well-known global auth-failure callback - see TrackingMap.tsx.
  gm_authFailure?: () => void
}
