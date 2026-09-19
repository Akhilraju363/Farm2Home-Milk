import axios from 'axios'
import { tokenStorage } from './tokenStorage'

/**
 * Builds an axios instance with the shared bearer-token + 401-redirect behavior.
 *
 * There is no runtime gateway in the $0 Render architecture (Vercel frontend + two standalone
 * Render services, no api-gateway) - so instead of one relative baseURL routed by a gateway/proxy,
 * each backend gets its own client pointed at its own absolute origin via a Vite env var. Locally
 * (env var unset), baseURL falls back to the same relative '/api/v1' path used before, which the
 * Vite dev proxy still forwards to the local api-gateway (see vite.config.ts) - local dev is
 * unaffected. See authAxiosClient.ts for the auth-service counterpart and
 * docs/FREE_DEPLOYMENT_ARCHITECTURE.md for the full routing design.
 *
 * `timeoutMs` defaults to 10s (unchanged for this client / every other service file that imports
 * the default export below). authAxiosClient.ts passes a longer value - see its own comment for
 * why. Increasing a client timeout never makes the server respond faster; it only changes how
 * long the browser waits before giving up on a slow-but-still-working request.
 */
export function createApiClient(baseURL: string, timeoutMs = 10000) {
  const client = axios.create({
    baseURL,
    headers: { 'Content-Type': 'application/json' },
    timeout: timeoutMs,
  })

  client.interceptors.request.use((config) => {
    const token = tokenStorage.getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error.response?.status === 401) {
        tokenStorage.clear()
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
  )

  return client
}

// backend/app (farm, production, customer, inventory, subscription, order, payment, delivery,
// notification, invoice, dashboard, reports) - everything except /auth/**.
const axiosClient = createApiClient(import.meta.env.VITE_BACKEND_API_URL || '/api/v1')

export default axiosClient
