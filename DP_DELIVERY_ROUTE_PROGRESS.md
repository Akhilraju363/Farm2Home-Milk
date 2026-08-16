# Automatic Delivery Route Selection — Progress Log

Branch: `compliance/dpdp` (not pushed). Farm2Home location used throughout:
`13.627088254663684, 78.96885065657058`, 10 KM delivery radius (farm-service's
`BusinessSettings`, unchanged, still the single source of truth).

---

## 1. Existing architecture (audit findings, before any code was written)

- **`DeliveryRoute`** (delivery-service, `delivery.delivery_routes`) already existed with full CRUD
  (`DeliveryRouteController`/`DeliveryRouteServiceImpl`), `routeName`/`routeCode`/`area`/`city`/
  `pincode`/`active`/soft-delete, and an admin Route Management UI
  (`RouteManagementPage.tsx`/`RouteFormDialog.tsx`) — all from prior session work. **Not a second
  system was built; this task extends it.**
- **`DeliveryAssignment`** (delivery-service) already has an `order_id` (plain UUID, no FK — order
  and delivery are different databases) and a real `route` FK. `manualAssign()` already existed,
  FARM_MANAGER/SUPER_ADMIN only, and already called order-service (`OrderServiceClient.getOrder`)
  to verify the order exists and is `PENDING` before creating an assignment.
- **Order creation does *not* automatically create a `DeliveryAssignment`.** Confirmed by reading
  `OrderServiceImpl` end to end: `checkout()`/`createManualOrder()` only ever create an `Order` row;
  a `DeliveryAssignment` is created later, either by `OrderEventConsumer` reacting to a Kafka
  `ORDER_CREATED` event (auto-assignment path, currently inert in this dev environment — Kafka has
  no broker) or by an admin explicitly calling `manualAssign()`. **This is why the route had to be
  stored on `Order` itself, not derived from an assignment that may not exist yet** (see §7).
- **`CustomerAddress`** already has `latitude`/`longitude` (from an earlier task); **delivery
  eligibility** was already backend-authoritative via `DeliveryAvailabilityServiceImpl.getForCustomer()`
  in customer-service, using the shared `GeoDistanceUtil.haversineKm()` against farm-service's
  `BusinessSettings`. Order-service already revalidated this at order-creation time
  (`OrderServiceImpl.verifyDeliveryEligibility`) via its own `CustomerServiceClient`.
- **No route-selection logic existed anywhere** — `DeliveryRoute` had no geographic fields at all
  (only `area`/`city`/`pincode` strings), and `AssignDeliveryDialog.tsx`'s Route field was a plain
  admin-picked dropdown with no connection to the customer's address.
- **Microservice dependency graph** (the specific thing this task's spec insisted be checked before
  writing anything): `order-service → customer-service, inventory-service, production-service`;
  `customer-service → farm-service`; `delivery-service → order-service (OrderServiceClient),
  payment-service`; `farm-service →` (nothing). **`delivery-service` already calls `order-service`.**
  This is the fact that shaped the whole design — see §7.

---

## 2. Selected routing strategy

Geographic, not string-matching. `DeliveryRoute` gained `centerLatitude`/`centerLongitude`/
`radiusKm` (all nullable — a route missing any of the three is simply never chosen automatically,
but stays fully usable for manual assignment, so no pre-existing route breaks). Selection uses the
**existing shared `GeoDistanceUtil.haversineKm()`** — the same calculation delivery-eligibility
already used, never Google Maps (which remains visualization-only, per the existing convention).

**Algorithm** (`DeliveryRouteSelectionServiceImpl.selectRoute(lat, lng)`):
1. Reject missing/out-of-range coordinates → empty (no call made).
2. Fetch active, non-deleted routes (delivery-service's existing `search(active=true)`).
3. Keep only routes with all three geo fields set.
4. Keep only routes whose **coverage circle actually contains the point**
   (`haversine(point, routeCenter) <= route.radiusKm`) — a route is never chosen just for having
   the nearest center if the point is actually outside its circle.
5. Among those, pick the **smallest distance to the route's own center**; ties broken by
   `routeCode` ascending — deterministic regardless of database row order.
6. No match → empty ("no route available"), logged at `ERROR` (a genuine coverage gap, since the
   seed layout — see §4 — is deliberately sized so this never happens for an address that already
   passed the 10 KM eligibility check).

---

## 3. Route data model

`area`/`city`/`pincode` were **kept, not removed** — the coverage circle is the sole input to
*selection*, but the strings remain genuinely useful for admin search/display, so removing them
would have been destructive for no benefit. New nullable columns:
`center_latitude DECIMAL(10,8)`, `center_longitude DECIMAL(11,8)`, `radius_km DECIMAL(6,2)`.
Validated server-side (`@DecimalMin/@DecimalMax` −90..90 / −180..180, `@Positive` for radius) and
client-side (same ranges, `RouteFormDialog.tsx`).

## 4. Initial 5 routes

Seeded via Flyway (`delivery-service/V6__add_route_geography.sql`), not application code or a
bootstrap script:

| Code | Name | Center | Radius |
|---|---|---|---|
| F2H-CENTRAL | Farm2Home Central | farm's own coordinates | 10.5 km |
| F2H-NORTH | Farm2Home North | 5 km north of farm | 7 km |
| F2H-EAST | Farm2Home East | 5 km east of farm | 7 km |
| F2H-SOUTH | Farm2Home South | 5 km south of farm | 7 km |
| F2H-WEST | Farm2Home West | 5 km west of farm | 7 km |

**Why these exact numbers, not the example boundaries in the spec:** Central's 10.5 KM radius
(centered exactly at the farm) *by construction* covers the entire 10 KM delivery disc with a
small margin — this is what guarantees §6's requirement ("Delivery available" must never be
followed by "No route available") rather than hoping four quadrant circles happen not to leave
gaps. The four directional routes exist for realistic operational subdivision and win the
nearest-center tie-break for addresses genuinely closer to them; Central is the guaranteed
fallback for anything they don't reach. `area`/`city`/`pincode` are the same for all five because
the entire real delivery area is one ~10 KM rural disc around a single farm — inventing five
distinct locality names would have been fabrication, which the spec explicitly forbade.
**Correction (see the addendum at the end of this file): the `city`/`pincode` values originally
seeded here (`Chikkaballapur`/`562101`) were themselves not Farm2Home's real address — no address
had ever been captured anywhere in the system at the time this was written, so a plausible-looking
nearby-city guess was used instead of verified real data. That was a mistake this task's own
"do not fabricate geographic data" instruction should have been read more strictly against. Fixed
in the addendum below with the real, verified address.**

## 5. Route-selection algorithm — see §2 (kept together to avoid duplication).

## 6. Order integration

`Order` gained a nullable `delivery_route_id UUID` column (no FK — cross-database reference, same
convention as `customerId`/`productId` elsewhere on `Order`). `OrderServiceImpl.verifyDeliveryEligibility()`
now returns the `DeliveryAvailabilityResponse` (previously `void`) instead of just validating it;
both `createManualOrder()` and `checkout()` read `routeId` off that response and pass it into
`buildAndSaveOrder()`, which sets it on the `Order` being built — **the same single code path both
order-creation flows already shared for pricing/stock reservation now also shares this.** The
customer can never submit a `routeId`: `CreateOrderRequest`/`CheckoutRequest` have no such field at
all (not "ignored if present" — it does not exist in the DTO), so there is nothing to trust or
distrust from the client.

## 7. Assignment integration — the circular-dependency decision

This was the one place the spec's own warning mattered concretely. `delivery-service` already
calls `order-service` (`OrderServiceClient`, used by `manualAssign()` to verify the order exists
and is `PENDING`). Two designs were possible:

- **Order-service calls delivery-service directly** for route selection at checkout time. Rejected:
  this would add `order-service → delivery-service` on top of the *already-existing*
  `delivery-service → order-service`, completing a genuine two-way HTTP dependency between the two
  services — exactly what the spec said to avoid.
- **Route selection lives in customer-service** (chosen). `customer-service → delivery-service` is
  a *new* edge, but delivery-service has no dependency back into customer-service, so it stays
  one-directional. `order-service → customer-service` already existed (used for delivery
  eligibility) — extending that one existing call's response to also carry `routeId` needed **zero
  new order-service dependencies**.

For **`manualAssign()`** itself, the order's own already-selected route is read off the very same
`OrderServiceClient.getOrder()` call `manualAssign()` already made (one new field,
`deliveryRouteId`, added to the existing local `OrderDetailResponse` projection) — **no new
dependency edge needed there either.** `ManualAssignRequest.routeId` changed from required to an
optional *override*: omitted → the order's own route is used and re-validated (exists, active);
provided → it's an explicit admin override, used instead. Since `manualAssign()` was already
FARM_MANAGER/SUPER_ADMIN-only, no new role gate was needed for the override capability itself.

**Final architecture** (see `DeliveryServiceClient`'s own Javadoc in customer-service for the
detailed reasoning, kept alongside the code):

```
Order creation:  OrderService -> CustomerService -> DeliveryRouteSelectionServiceImpl -> DeliveryService (read-only, routes)
Manual assign:   DeliveryService -> OrderService (existing call, now also reads deliveryRouteId)
```

No cycle in either direction.

## 8. API changes

- `POST/PUT /delivery/routes` (create/update): now accept optional `centerLatitude`/
  `centerLongitude`/`radiusKm`.
- `GET /delivery/routes*`: responses now include the same three fields (null if unset).
- `POST /delivery/assignments` (`manualAssign`): `routeId` is now optional (override only); 422
  (`BusinessException`) when neither an override nor the order's own route exists; 404 for an
  unknown route id; 409 for an inactive route — unchanged shapes, just a different source for the
  "which route" decision.
- `GET /customers/me/delivery-availability` and the admin `{id}` counterpart: response gained
  `routeId`/`routeName`, populated only when `deliveryAvailable === true`.
- `GET/POST /orders`, `POST /orders/checkout`: `OrderResponse` gained `deliveryRouteId`. No new
  public route-selection endpoint was added — per the spec's own preference, this stays an internal
  service-to-service concern (`DeliveryRouteSelectionServiceImpl`), not a new public API.
- Route-coverage validation endpoint (spec §21, explicitly optional): **not built.** The seed
  layout already guarantees full coverage by construction (§4), and the existing per-route CRUD
  validation (lat/lng/radius ranges) already prevents an individually-invalid route from being
  created — a whole extra admin diagnostic endpoint for a problem the design already prevents
  would have been over-engineering for this task, per the spec's own instruction not to add it
  unless it fit naturally.

## 9. Database migrations

- `delivery-service/V6__add_route_geography.sql` — adds the 3 columns, seeds the 5 real routes.
- `order-service/V6__add_delivery_route_id.sql` — adds `orders.delivery_route_id`.
- No migration needed in customer-service (nothing new persisted there — selection is computed live
  on every call, not cached).

## 10. Frontend changes

- **Customer checkout is unchanged in kind** — it never had a route dropdown to remove (Cart/
  Checkout didn't exist before the prior session's purchase-flow task). `ShopPage.tsx`/
  `CheckoutPage.tsx`'s existing delivery-availability banner now also shows *"Estimated delivery
  area: Farm2Home North"* when available — **route name only, never the internal `routeCode`**, per
  the spec's explicit instruction.
- **`AssignDeliveryDialog.tsx`**: the free-choice Route `Autocomplete` is gone from the default
  path. Once an order is selected, the dialog shows a read-only **"Automatically Selected Route: Farm2Home
  North (F2H-NORTH)"** box. A **"Change Route"** button (shown because the dialog is already
  FARM_MANAGER/SUPER_ADMIN-gated) reveals the original dropdown only when an admin deliberately asks
  to override, or when the order genuinely has no auto-selected route (old order / coverage gap) —
  in which case a picker is mandatory since the backend needs some route.
- **`RouteFormDialog.tsx`**: added Center Latitude / Center Longitude / Coverage Radius fields with
  the same numeric-range validation as the backend; existing Area/City/Pincode fields kept
  (still genuinely useful, not misleading).
- **`OrderDetailsPage.tsx`** (admin view): the Delivery section now shows Delivery Route / Route
  Code / Route Status, resolved live from `order.deliveryRouteId` via delivery-service's own
  existing route-lookup endpoint — **not calculated in the frontend**, and independent of whether a
  `DeliveryAssignment` exists yet (per §7's "Order → selectedDeliveryRoute" design).
- **`DeliveryDetailsPage.tsx`**: unchanged — it already showed `routeName`/`routeCode` from
  `AssignmentResponse` (reused as instructed, not duplicated).

## 11. Authorization

No role sets changed. Route CRUD stays FARM_MANAGER/SUPER_ADMIN-only (unchanged
`@PreAuthorize`); `manualAssign` stays FARM_MANAGER/SUPER_ADMIN-only (its override capability rides
on the same existing gate, not a new one); delivery-availability/route-search endpoints stay open
to any authenticated caller (unchanged) since they carry no route-mutation capability, only reads.

## 12. Tests

- `delivery-service`: `DeliveryAssignmentServiceImplTest$ManualAssign` rewritten for the
  order-first/route-from-order flow — 10 tests (auto-select from order, explicit override
  precedence, no-route-anywhere → 422, duplicate assignment, partner/route not-found/inactive,
  order not-found/not-eligible). Full suite: **128/130 pass** (2 pre-existing Docker-gated
  failures, unrelated).
- `customer-service`: new `DeliveryRouteSelectionServiceImplTest` — 16 tests covering the real
  5-route layout (central/north/east/south/west selection, the 10 KM boundary, far-outside →
  empty), edge cases (missing/invalid coordinates, a route missing geo fields, no active routes,
  delivery-service unreachable), and deterministic tie-break (equal distance → lower routeCode;
  nearest-covering-route wins over widest-radius route). `DeliveryAvailabilityServiceImplTest`
  extended with 3 new integration tests (route populated when eligible, left null on a coverage
  gap, never attempted when ineligible). Full suite: **153/153 pass.**
- `order-service`: `OrderServiceImplTest` extended with tests proving `deliveryRouteId` is
  persisted verbatim from the availability response (never re-derived) for both
  `createManualOrder` and `checkout`, and stays null on a coverage gap. Full suite: **102/104 pass**
  (2 pre-existing Docker-gated failures, unrelated). Customer-submitted `routeId` has no test
  because it has no code path to test — `CreateOrderRequest`/`CheckoutRequest` simply have no such
  field.

## 13. Live verification (real services, real accounts, LIVE API VERIFIED + BROWSER VERIFIED)

All five real routes confirmed seeded correctly via direct query. A fresh test customer with five
addresses (one at each route's own center, one genuinely out-of-radius) confirmed via
`GET /customers/me/delivery-availability?addressId=...`:

| Address | `deliveryAvailable` | `routeName` |
|---|---|---|
| At farm center | true | Farm2Home Central |
| 5 km north | true | Farm2Home North |
| 5 km east | true | Farm2Home East |
| 5 km south | true | Farm2Home South |
| 5 km west | true | Farm2Home West |
| ~166 km away (Bengaluru) | false | *(null)* |

**Order creation** (`POST /orders`, North-default address) → `deliveryRouteId` in the response
matched North's real route id. **Manual assignment with no `routeId` in the request** →
`DeliveryAssignmentServiceImplTest`'s auto-select logic confirmed live: the created assignment's
`routeCode` was `F2H-NORTH`, matching the order's own route, entirely without the client sending
one. **Explicit override** (`routeId` = Central's id, on an order whose own route was North) →
assignment used Central, confirming override precedence. **Concurrent-purchase-style negative
test**: out-of-radius address → `deliveryAvailable:false`, `routeId:null`, matching the existing
(unchanged) eligibility-rejection behavior.

**Browser-verified** (Playwright, headless Chromium, 1600px desktop + 390px mobile, light + dark):
Checkout's "Estimated delivery area: Farm2Home North" banner (light and dark, both good contrast);
admin Order Details showing Delivery Route / Route Code / Route Status (Active), independent of any
assignment, on both desktop and mobile; the Assign Delivery dialog showing the read-only
"Automatically Selected Route: Farm2Home North (F2H-NORTH)" box with no dropdown, then completing
a real end-to-end assignment through that exact UI (order search → partner pick → Assign →
confirmed via API that the resulting assignment used the auto-selected route); Route Management's
Create Route form showing the new Center Latitude/Longitude/Coverage Radius fields.

## 14. Known limitations

- Auto-assignment of a *delivery partner* remains out of scope, as instructed (§15) — route
  selection is automatic, partner assignment is still the existing manual admin workflow.
- Kafka is down in this dev environment (pre-existing, unrelated to this task), so
  `OrderEventConsumer`'s auto-assignment-on-`ORDER_CREATED` path is inert; `manualAssign` is the
  only exercised assignment path here. The route-selection logic itself does not depend on Kafka at
  all (it runs synchronously at order-creation time in `order-service`), so this doesn't affect the
  feature's correctness, only which assignment-creation path was actually exercised live.
- If an admin changes a customer's *default* address after an order already exists, the order's own
  `deliveryRouteId` is **not** retroactively recalculated (it was fixed at order-creation time, by
  design — an order's committed delivery route shouldn't silently shift under an in-flight
  delivery). §13's "address changed before order" scenario is satisfied (a *new* order after an
  address change correctly gets the new route); a full "recalculate an existing PENDING order's
  route after an address edit" flow was not requested by the order-integration section and was not
  built.

## 15. Future improvements

- A real route-coverage admin diagnostic endpoint (§21) if the route layout grows more complex than
  the current guaranteed-coverage-by-construction design.
- Partner auto-assignment (deliberately out of scope here, §15).
- If Kafka becomes available in this environment, `OrderEventConsumer`'s existing auto-assignment
  path would then also carry the order's route through it (it already reads
  `Order`/`OrderEvent` fields today; carrying `deliveryRouteId` through that path would be a small,
  separate follow-up, not touched in this task to keep the change scoped to what was asked).

## 16. Test data cleanup

Every account/order/address/assignment/partner created for live verification (1 test customer +
5 addresses, 1 test admin, 1 test delivery-partner auth account + profile, 3 test orders, 1 test
assignment) was removed via real APIs where they existed (order cancellation) and direct SQL for
entities with no delete endpoint (assignments, delivery partner, addresses, customer profile, auth
accounts) — confirmed via a final count query showing zero residual rows. **The 5 real F2H-* routes
were not touched** — they are the actual seeded business data this task was asked to create, not
test artifacts.

---

## Final Status

| Item | Status |
|---|---|
| Route creation (5 real routes, Flyway-seeded) | 🟢 READY |
| Route selection (geographic, single authoritative implementation) | 🟢 READY |
| 10 KM validation (reused unchanged) | 🟢 READY |
| Order integration (`deliveryRouteId`, never client-trusted) | 🟢 READY |
| Assignment integration (auto-select + admin override) | 🟢 READY |
| Admin UI (Order Details, Assign Delivery, Route Management) | 🟢 READY |
| Customer UI (Shop/Checkout "estimated delivery area") | 🟢 READY |
| Backend tests (delivery/customer/order-service) | 🟢 READY — 383/387 total, all 4 non-passing are pre-existing Docker-gated environmental tests, zero regressions |
| Frontend tests (`tsc`, production build) | 🟢 READY — clean |
| Live verification (API + browser, desktop/mobile, light/dark) | 🟢 READY |

---

## Addendum — Farm location / route location display fix

### What was wrong

The Route Management screen displayed **"Within 10 km of Farm2Home, Chikkaballapur"**.
`Chikkaballapur` was never Farm2Home's real location — at the time §4/§8 above were written, no
business address existed anywhere in the system (`BusinessSettings` had only coordinates + radius,
no address fields), so a plausible-sounding nearby city was used as the five routes' `city`
column instead of verified real data. **This was database seed data** (§8's own
`V6__add_route_geography.sql`, confirmed by repo-wide search — not a hardcoded frontend string,
not a backend-generated description, not a config value; the frontend's Route Management table
simply rendered `${route.area}, ${route.city}` verbatim from the API response).

Farm2Home's **real** address:

```
ST Colony, Yerraguntlapalle, Pileru,
Chittoor, Andhra Pradesh - 517214
```

Coordinates (unchanged, already correct, confirmed by re-auditing `farm.business_settings`
directly): `13.627088254663684, 78.96885065657058`. Radius: unchanged, `10 KM`.

### What was fixed

1. **`farm-service`**: `BusinessSettings` gained real address fields (`business_name`,
   `address_line`, `locality`, `city`, `district`, `state`, `pincode`) — this is now the **one**
   authoritative place Farm2Home's address is stored anywhere in the codebase.
   `V7__add_business_address.sql` adds the columns and populates the real address (the existing,
   already-correct coordinates/radius were left untouched). Display-only for now — not added to
   `UpdateBusinessSettingsRequest`, since this task asked for correct *display*, not a new address
   edit form; coordinates/radius keep their existing SUPER_ADMIN/FARM_MANAGER-gated edit flow.
2. **`delivery-service`**: `V7__correct_route_location.sql` (the old, already-applied
   `V6__add_route_geography.sql` was **not** edited, per the "never edit an applied migration"
   rule) updates the 5 real routes' `city`/`pincode` from `Chikkaballapur`/`562101` to
   `Pileru`/`517214`.
3. **Frontend `RouteManagementPage.tsx`**: the Area column no longer concatenates `city` at all —
   it shows only `route.area` ("Within 10 km of Farm2Home"), never a per-route locality string.
   This is defense in depth on top of the data fix: even if a route's `city` field were wrong
   again in the future, the UI can no longer surface it in this column.
4. **Frontend `FarmsPage.tsx`** (Settings → Farm & Business → Delivery Coverage): now displays the
   real address block above the existing Latitude/Longitude/Delivery Radius fields, sourced
   entirely from `BusinessSettings`.

### What was verified unchanged (per the task's explicit "do not change unless the audit finds a
real problem" instructions)

- **Automatic route selection** (`DeliveryRouteSelectionServiceImpl` in customer-service) was
  re-audited and confirmed to use `GeoDistanceUtil.haversineKm()` against
  `centerLatitude`/`centerLongitude`/`radiusKm` exclusively — no city/address string is read by
  the selection algorithm anywhere. **Not modified.**
- **10 KM eligibility** (`DeliveryAvailabilityServiceImpl`) — same confirmation, coordinates only.
  **Not modified.**
- **Google Maps** — the API key config (`VITE_GOOGLE_MAPS_API_KEY`) was not touched, not rotated,
  and was already independent of business-location data (it renders/geocodes visually; Farm2Home's
  own location was always sourced from `BusinessSettings`/Haversine, never from Maps). Confirmed
  no Maps code anywhere reads or writes `BusinessSettings`.
- Route names/codes (`F2H-CENTRAL`/`NORTH`/`EAST`/`SOUTH`/`WEST`, `Farm2Home Central`/etc.) —
  unchanged, since they represent delivery zones, not the city name, matching the task's explicit
  instruction not to rename them.

### Post-fix verification

Repo-wide search for `Chikkaballapur`/`562101` after the fix returns matches only inside the two
migration files themselves (`V6`'s original, deliberately-unedited wrong seed values, and `V7`'s
own comment describing what it corrects) — no application code, test, or displayed string
contains the incorrect location anymore. `GET /farm/business-settings` returns the real address;
`GET /delivery/routes/search` returns `city: "Pileru"`, `pincode: "517214"` for all five real
routes; Route Management's Area column renders `"Within 10 km of Farm2Home"` with no city suffix.
