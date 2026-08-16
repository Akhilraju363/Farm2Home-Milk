# Google Maps Setup (Frontend Delivery Tracking)

The delivery tracking map (`frontend/src/components/delivery/TrackingMap.tsx`, used by Customer
Tracking at `/orders/:id/tracking` and Admin Tracking at `/delivery/tracking`) renders via the
Google Maps JavaScript SDK (`@react-google-maps/api`), keyed by a Vite environment variable. The
key is a frontend-only concern — it is read via `import.meta.env` and never sent to any Farm2Home
backend service (delivery-service, order-service, customer-service, payment-service, the API
gateway, or the database). The backend location APIs (`POST`/`GET
/api/v1/delivery/assignments/{id}/location(s)`, 15s polling, ownership checks, LIVE/RECENT/STALE
freshness) are unchanged — only the map rendering layer changed.

## 1. Get a Google Maps API key

1. Create (or reuse) a project in the [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **Maps JavaScript API** for that project.
3. Under **APIs & Services → Credentials**, create an API key.
4. Google requires billing to be enabled on the project even for local development — there is no
   API key that works without an attached billing account. A generous free monthly usage tier
   applies, but the key itself is not "free" to create.

## 2. Configure the key locally

Create `frontend/.env.local` (already gitignored — see `frontend/.gitignore`/root `.gitignore`;
never commit this file):

```
VITE_GOOGLE_MAPS_API_KEY=YOUR_KEY
```

`frontend/.env.example` documents the variable name only, with no value, and is the file that's
committed:

```
VITE_GOOGLE_MAPS_API_KEY=
```

## 3. Restart the dev server

Vite only reads `.env*` files at startup:

```
cd frontend
npm run dev
```

## 4. Verify

1. Open the Customer Tracking page for an order that's `OUT_FOR_DELIVERY`
   (`/orders/:id/tracking`), or Admin Tracking (`/delivery/tracking`) with at least one active
   delivery.
2. The map should render with a marker at the delivery partner's real last-submitted location.
3. If `VITE_GOOGLE_MAPS_API_KEY` is unset or empty, the map area shows "Google Maps API key is not
   configured." instead of crashing — the rest of the page (order info, delivery status, partner
   info, last-location time, freshness chip) still renders normally.
4. If the key is set but invalid/misconfigured, the map area shows "Unable to load Google Maps.
   Please try again later." — same graceful degradation.

## 5. Restricting the key (do this before using a real key beyond local dev)

This setup does not configure Google Cloud restrictions automatically. Before using a key outside
of local development, restrict it under **APIs & Services → Credentials → (your key) → Application
restrictions → HTTP referrers**:

- Local development: `http://localhost:3000/*` (and `http://127.0.0.1:3000/*` if used)
- Farm2Home development/staging domain(s), once assigned
- Farm2Home production domain(s), once assigned

Also restrict the key to the **Maps JavaScript API** only under **API restrictions**, so a leaked
key can't be used against other Google Cloud APIs on the same project.

## Where the key is read

`frontend/src/utils/googleMapsConfig.ts` is the single place the env var is read
(`import.meta.env.VITE_GOOGLE_MAPS_API_KEY`) and exposes `isGoogleMapsConfigured()`. Nothing else
in the frontend should read `VITE_GOOGLE_MAPS_API_KEY` directly.
