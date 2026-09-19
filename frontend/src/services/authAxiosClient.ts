import { createApiClient } from './axiosClient'

// auth-service - the /api/v1/auth/** surface (register, login, OTP, refresh, google, me, logout).
// See axiosClient.ts for why this is a separate client (no runtime gateway in production) and
// docs/FREE_DEPLOYMENT_ARCHITECTURE.md for the full routing design.
//
// 20s timeout (vs the 10s default every other client keeps) - auth-service's login/register do a
// BCrypt(12) hash on every call, and measured Render Free behavior (0.1 vCPU) put a single such
// request at ~8.2s once the JVM is warm, and far higher (~30s) on the very first request after a
// cold start (see docs/PRODUCTION_READINESS.md). 20s covers the warm case with real margin
// without making a stuck/looping request look "fine" for a full half-minute-plus. This is a
// client-side patience adjustment ONLY - it does not make auth-service respond faster, and it does
// NOT cover Render's much longer full spin-down-to-healthy cold start (minutes, not seconds);
// that needs a different mitigation (loading-state UX, a keep-alive ping, or accepting the risk)
// and was deliberately left as a flagged, undecided item rather than papered over here - see
// docs/PRODUCTION_READINESS.md §10.
const authAxiosClient = createApiClient(import.meta.env.VITE_AUTH_API_URL || '/api/v1', 20000)

export default authAxiosClient
