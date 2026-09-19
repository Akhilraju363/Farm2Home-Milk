# Environment Variables — $0 Deployment (Render + Vercel + Neon)

No values, no secrets. See `docs/FREE_DEPLOYMENT_ARCHITECTURE.md` for the full design this
supports. "Required" means the service will not behave correctly (or, for `JWT_SECRET` under the
`prod` profile, will not start) without it; "Optional" has a safe default.

## auth-service (Render Web Service)

| Variable | Required? | Default if unset | Notes |
|---|---|---|---|
| `JWT_SECRET` | **Required** | committed dev placeholder (`application.yml`) | Base64, ≥256-bit random value. **Must be byte-identical to `backend/app`'s `JWT_SECRET`** — auth-service signs, backend/app only verifies. Under `SPRING_PROFILES_ACTIVE` including `prod`, `JwtSecretGuard` refuses to start if this is blank or still the committed placeholder. Rotating it invalidates every existing token on both services simultaneously — a coordinated two-service change. |
| `SPRING_PROFILES_ACTIVE` | Recommended | `default` | Set to `render,prod` on Render: `render` applies `application-render.yml` (Eureka off, smaller DB pool defaults, Config-Server health check off); `prod` activates `JwtSecretGuard`. |
| `PORT` | Required (Render sets this automatically) | `8081` | Render injects this; do not set manually. The service binds `server.port=${PORT:8081}`. |
| `SPRING_DATASOURCE_URL` | **Required** | `jdbc:postgresql://localhost:5432/farm2home?currentSchema=auth` | Neon connection string, e.g. `jdbc:postgresql://<host>/<db>?currentSchema=auth&sslmode=require`. Use Neon's pooled endpoint (see architecture doc §10/§11). |
| `SPRING_DATASOURCE_USERNAME` | **Required** | `farm2home` | Neon role name. |
| `SPRING_DATASOURCE_PASSWORD` | **Required** | `farm2home@123` | Neon role password. Never commit a real value. |
| `CORS_ALLOWED_ORIGINS` | **Required** for production | `http://localhost:3000,http://localhost:5173` | Comma-separated exact origins. Must include the real Vercel production origin, and must match `backend/app`'s value. |
| `EUREKA_CLIENT_ENABLED` | Optional | `true` | Set `false` if not using the `render` profile (the `render` profile already sets this). No Eureka exists in this deployment. |
| `DB_POOL_MAX_SIZE` | Optional | `10` (`5` under the `render` profile) | Hikari `maximum-pool-size`. See connection-budget math in the architecture doc §11. |
| `DB_POOL_MIN_IDLE` | Optional | `2` (`0` under the `render` profile) | Hikari `minimum-idle`. |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | Optional | `30000` | Hikari `connection-timeout`. |
| `DB_POOL_IDLE_TIMEOUT_MS` | Optional | `600000` | Hikari `idle-timeout`. |
| `DB_POOL_MAX_LIFETIME_MS` | Optional | `1800000` | Hikari `max-lifetime`. |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Optional | `localhost:9092` | No broker exists in this deployment; producers are lazy/non-fatal (`max.block.ms=1500`, `@Async`). Safe to leave unset. |
| `GOOGLE_CLIENT_ID` | Optional | blank | Google OAuth **client ID only** — no client secret exists anywhere in this codebase (ID-token verification, not an authorization-code exchange). Blank disables Google Sign-In cleanly (service still starts; `POST /auth/google` returns a clean 401). Must equal the frontend's `VITE_GOOGLE_CLIENT_ID`. |
| `OTP_EXPIRY_SECONDS` / `OTP_MAX_ATTEMPTS` / `OTP_RESEND_COOLDOWN_SECONDS` / `OTP_MAX_RESENDS` | Optional | `300` / `5` / `30` / `3` | OTP business rules — unrelated to deployment, listed for completeness. |
| `FARM2HOME_APP_INTERNAL_SECRET` | Not applicable to auth-service | — | This is a `backend/app`-only variable (see below) — auth-service makes no in-process loopback calls and never reads it. Listed here only to be explicit that it does **not** apply to auth-service. |

## backend/app (Render Web Service)

| Variable | Required? | Default if unset | Notes |
|---|---|---|---|
| `JWT_SECRET` | **Required** | committed dev placeholder | **Must be byte-identical to auth-service's `JWT_SECRET`.** This service only verifies, never mints. |
| `PORT` | Required (Render sets this automatically) | `8080` | Already `server.port: ${PORT:8080}` from the Group 1-6 spike — no change needed. |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | **Required** | local Postgres defaults | Neon connection string covering all 10 embedded schemas (farm, production, customer, inventory, subscription, order, payment, delivery, notification, invoice). |
| `CORS_ALLOWED_ORIGINS` | **Required** for production | `http://localhost:3000,http://localhost:5173` | Must match auth-service's value exactly. |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` / `DB_POOL_CONNECTION_TIMEOUT_MS` / `DB_POOL_IDLE_TIMEOUT_MS` / `DB_POOL_MAX_LIFETIME_MS` | Optional | `10` / `0` / `30000` / `600000` / `1800000` | Same mechanism as auth-service; see connection-budget math in the architecture doc §11. |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Optional | `localhost:9092` | Already lazy/dormant from Group 1-6 (`KafkaDisabledConfig`). |
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | Optional | `farm2home-app` | Must be present (even though consumers never start) — order-service's `KafkaConfig` reads it. |
| `FARM2HOME_SCHEDULING_ENABLED` | Optional | `true` | Keep `true` in production (Render runs exactly 1 instance, no ShedLock needed). |
| `PAYMENT_LEGACY_CALLBACK_ENABLED` | Optional | `false` | **Must stay `false`** — the legacy callback endpoint has no signature verification. Unrelated to this task; listed for completeness since it lives in the same file. |
| `PAYMENT_GATEWAY_PROVIDER` / `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` | Optional | `mock` / blank | Only needed if switching on real Razorpay — out of scope for this task. |
| `FARM2HOME_APP_INTERNAL_SECRET` | Optional (and should stay unset) | freshly random per process | The in-process loopback proof-of-origin secret (`InternalCallToken`). **Never set this from outside the container, never expose it to the frontend or any Vite env var.** The random per-process default is strictly stronger than any static value you could configure — this variable exists only for a scenario this deployment does not have (multiple `backend/app` replicas needing to agree on one secret), which does not apply on Render Free's single instance. |
| `MAIL_FROM` | Optional | `no-reply@farm2homemilk.example` | Display value only; `email.enabled` stays `false` by default (no SMTP configured), so no mail is actually sent regardless. |
| `DASHBOARD_DOWNSTREAM_TIMEOUT_MS` | Optional | `3000` | Dashboard fan-out per-call timeout — unrelated to this task. |

## Frontend (Vercel project environment variables)

| Variable | Required? | Default if unset | Notes |
|---|---|---|---|
| `VITE_AUTH_API_URL` | **Required** for production | unset → relative `/api/v1` | auth-service's public Render HTTPS URL, e.g. `https://<auth-service>.onrender.com/api/v1` (include the `/api/v1` suffix — the client does not add it). Unset in local dev on purpose — see architecture doc §8. |
| `VITE_BACKEND_API_URL` | **Required** for production | unset → relative `/api/v1` | `backend/app`'s public Render HTTPS URL, e.g. `https://<backend-app>.onrender.com/api/v1`. |
| `VITE_GOOGLE_CLIENT_ID` | Optional | blank (Google button disabled) | Must equal auth-service's `GOOGLE_CLIENT_ID`. Public value, not a secret. |
| `VITE_GOOGLE_MAPS_API_KEY` | Optional | blank | Pre-existing, unrelated to this task. |

## What must never be set as a frontend/Vite variable

- `X-Internal-Auth` / `FARM2HOME_APP_INTERNAL_SECRET` — this is `backend/app`'s in-process
  loopback proof-of-origin secret. It is generated fresh per process, is never returned by any
  endpoint, and the frontend must never send it. Verified in this task: no frontend source file
  references it, and forging it from an external request (wrong value, or even the loopback check
  alone) is rejected with 401 — see `FREE_DEPLOYMENT_ARCHITECTURE.md` §6.
- `X-User-Id` / `X-User-Mobile` / `X-User-Roles` — gateway/system-identity-owned headers.
  `backend/app`'s security filter strips any client-supplied copy of these before they can reach
  authorization logic; the frontend must only ever send `Authorization: Bearer <JWT>`.

## Coordinated (must-match-across-services) variables

| Variable | Must match between |
|---|---|
| `JWT_SECRET` | auth-service ↔ backend/app |
| `CORS_ALLOWED_ORIGINS` | auth-service ↔ backend/app |
| `GOOGLE_CLIENT_ID` (backend) ↔ `VITE_GOOGLE_CLIENT_ID` (frontend) | auth-service ↔ Vercel |
