# Automatic Delivery Partner Assignment — Progress Log

Branch: `compliance/dpdp` (not pushed). Farm2Home location/route-selection (prior task) is
unchanged and reused as-is throughout.

---

## 1. Existing architecture (audit findings, before any code was written)

Read end to end before writing anything, as instructed:

- **`DeliveryPartner`** (delivery-service): `active`, `deleted`, and a single `route` FK (a partner
  belongs to exactly one route, not many).
- **`DeliveryAssignment`**: `orderId`, `customerId`, `orderNumber`, `deliveryPartner` FK, `route`
  FK, `status` (ASSIGNED/OUT_FOR_DELIVERY/DELIVERED/FAILED), `assignedAt`. Already has a duplicate
  guard (`existsByOrderId`) used by `manualAssign()`.
- **`DeliveryAssignmentServiceImpl.manualAssign()`** already existed and already worked correctly
  (fixed in the prior route-selection task to resolve the order's own `deliveryRouteId` by
  default, with an explicit admin override) — **left untouched by this task**, still the manual
  fallback.
- **An `OrderEventConsumer` already existed** in delivery-service, listening to order-service's
  `ORDER_CREATED` Kafka event (`order.events` topic) — **automatic assignment was already
  partially implemented**, just broken in two concrete ways (see §3):
  1. Its partner query (`DeliveryPartnerRepository.findLeastLoadedPartners`) had **no route
     filter at all** — it picked the globally least-loaded active partner across every route,
     violating the "same route" eligibility rule this task requires.
  2. It built the new assignment using **`partner.getRoute()`** (the partner's own route), never
     `Order.deliveryRouteId` (which didn't even exist on the Kafka event payload) — exactly the
     "Order: North / Assignment: East" bug §19 of the task explicitly warned against.
- **`OrderEvent`** (shared `common-events`) carried `orderId`/`customerId`/`orderNumber`/etc., but
  **no `deliveryRouteId`** — the automatic-selection route-selection task's own result never
  reached delivery-service's Kafka consumer at all.
- **`DELIVERY_ASSIGNED`** notification (SMS/PUSH/EMAIL templates, `NotificationClassifier`,
  `notification-service`'s Kafka consumer) **already existed and already worked**, triggered by
  `DeliveryEventProducer.publishDeliveryEvent(assignment, "DELIVERY_ASSIGNED")` — the same call
  `manualAssign()` already made. **No new notification type/channel was needed.**
- **`Order.status`** already transitions `PENDING → ASSIGNED` automatically whenever *any*
  `DeliveryAssignment` is created (order-service's `DeliveryEventConsumer`, reacting to
  `delivery.events`) — meaning **`GET /orders/summary`'s existing `pendingOrders` count already
  is** "orders waiting for assignment," with zero new backend work required for that dashboard
  stat.
- **`MyDeliveriesPage.tsx`** already renders whatever assignments `GET /delivery/assignments/search`
  returns for the caller's own `DeliveryPartner` profile — automatic and manual assignments are the
  same entity through the same ownership-scoped query, so **no frontend change was needed there
  at all**; a partner automatically sees a new automatic assignment already.
- **Dependency graph**: `delivery-service → order-service` (`OrderServiceClient`, used by
  `manualAssign`) and `delivery-service → payment-service` already existed. **No edge from
  order-service back into delivery-service exists anywhere.**

## 2. Existing manual assignment

Completely unchanged by this task. `manualAssign()`'s signature, validation order (partner →
duplicate-check → order lookup/eligibility → route resolution), and its FARM_MANAGER/SUPER_ADMIN
`@PreAuthorize` are exactly as the prior task left them. Automatic assignment is a genuinely
additive workflow: if it succeeds, the order simply already has an assignment by the time an admin
would have manually created one (and the dialog now says so, see §12); if it doesn't (no eligible
partner, or Kafka down), manual assignment is the only path, unchanged.

## 3. Automatic assignment design

`OrderEventConsumer` was fixed, not rewritten from scratch — same class, same `@KafkaListener`,
same idempotency guard, same overall shape, with its two real bugs corrected and the
partner-selection logic extracted into a new, reusable, unit-testable service (§4).

```
ORDER_CREATED (Kafka, order.events)
   -> OrderEventConsumer.consume()
      -> already assigned? (existsByOrderId) -> skip
      -> event.deliveryRouteId null?          -> skip (order stays unassigned)
      -> route still exists (findByIdAndDeletedFalse)? -> skip if not
      -> PartnerSelectionServiceImpl.selectLeastLoadedPartner(routeId)
         -> no eligible partner -> skip (order stays unassigned)
         -> partner found -> create DeliveryAssignment(autoAssigned=true), publish DELIVERY_ASSIGNED
```

Every "skip" path is a deliberate no-op with a log line — never an exception thrown back at Kafka,
never a fake/partial assignment, matching §2's explicit "do not fail the customer's order" and
§17's "do not create a fake assignment" instructions exactly.

## 4. Partner selection algorithm

New `PartnerSelectionServiceImpl` — **the one authoritative implementation**, called only from
`OrderEventConsumer` (manual assignment deliberately does not call it; an admin picks any partner
explicitly, by design).

1. **Eligibility**: `active = true`, `deleted = false`, `route.id` = the order's own
   `deliveryRouteId` — enforced by the repository query itself
   (`findEligiblePartnersForRouteForUpdate`), not filtered in application code.
2. **Workload**: live `COUNT` of the partner's `ASSIGNED`/`OUT_FOR_DELIVERY` assignments
   (`DeliveryAssignmentRepository.countByDeliveryPartner_IdAndStatusIn`) — `DELIVERED`/`FAILED`
   never count. Not a persisted field anywhere (§5's explicit instruction) — computed fresh on
   every call and also exposed read-only on `PartnerResponse`/`AssignmentResponse` for admin UI
   (§10/§26), so it can never drift from the assignments it counts.
3. **Selection**: `Stream.min()` over workload, **tie-broken by `DeliveryPartner.id` ascending** —
   deterministic regardless of database row order or which candidate is examined first (unit
   tested explicitly, see §14).

## 5. Workload calculation

No new persisted field — reused the exact `ASSIGNED`/`OUT_FOR_DELIVERY` convention the
route-selection task's own `DeliveryRouteServiceImpl` (deletion guard) and the admin dashboard
already used. One new repository method
(`DeliveryAssignmentRepository.countByDeliveryPartner_IdAndStatusIn`) — a plain `COUNT` query, not
a cached/materialized value.

## 6. Route matching

`Order.deliveryRouteId` (from the prior route-selection task) is carried on the Kafka event
(`OrderEvent.deliveryRouteId`, new field) and used **as-is** — `OrderEventConsumer` never
recalculates or substitutes it (fixing the old code's exact bug of using `partner.getRoute()`
instead). Confirmed via a dedicated unit test (`usesOrdersOwnRoute_notAnySubstitute`) that the
saved assignment's route is always the order's route, never anything else.

## 7. Kafka/event flow

- **`OrderEvent`** (shared `common-events`) gained `deliveryRouteId` (nullable `UUID`) - order-service's
  `OrderEventProducer` now populates it from `Order.deliveryRouteId`. Purely additive; every other
  existing consumer of `order.events` is unaffected (Jackson ignores unknown/absent fields).
- **No new Kafka topic, consumer, or producer was added** — the existing `ORDER_CREATED` →
  `OrderEventConsumer` → `DELIVERY_ASSIGNED` chain was completed, not duplicated, per §15's
  explicit instruction.
- **Architecture/circular-dependency check** (§16, the task's own required audit): delivery-service
  already calls order-service (`OrderServiceClient`, `manualAssign`'s order lookup). Automatic
  assignment needed the order's route at Kafka-consume time; carrying it **on the event itself**
  (rather than delivery-service calling order-service synchronously from inside the Kafka listener)
  avoids adding a second HTTP dependency in that direction and keeps the whole flow event-driven,
  exactly as §16 preferred. No new dependency edge was introduced anywhere.

## 8. Notification flow

Unchanged and reused exactly: `eventProducer.publishDeliveryEvent(assignment, "DELIVERY_ASSIGNED")`
is the same call both `manualAssign()` and the fixed `OrderEventConsumer` make.
`NotificationClassifier`/the existing `DELIVERY_ASSIGNED_SMS`/`DELIVERY_ASSIGNED_PUSH`/
`DELIVERY_ASSIGNED_EMAIL` templates are untouched. **No new notification type or channel was
created.**

## 9. Concurrency strategy

Two orders on the same route arriving close together must never both see "partner B has 0 active
deliveries" and both pick B. `DeliveryPartnerRepository.findEligiblePartnersForRouteForUpdate` now
carries `@Lock(LockModeType.PESSIMISTIC_WRITE)`: it locks every eligible partner row for the route
for the remainder of the caller's transaction. A second transaction racing to auto-assign another
order on the *same* route blocks at that query until the first transaction commits (making its new
`DeliveryAssignment` — and the resulting workload increase — visible) or rolls back. This is the
same "lock the candidate set, then decide" pattern already established in this codebase
(inventory-service's atomic stock-decrement `UPDATE...WHERE`), just expressed as a row lock instead
of a single `UPDATE` because the decision here is *which* row to pick, not a single column mutation.
`PartnerSelectionServiceImpl.selectLeastLoadedPartner` is itself `@Transactional`, and
`OrderEventConsumer.consume()` is `@Transactional` around the whole flow, so the lock is held from
partner selection through the assignment `save()`.

**Not over-engineered**: no new locking table, no distributed lock, no scheduler — a single
`@Lock` annotation on an existing-shape repository query, scoped per-route so unrelated routes'
assignments never block each other.

## 10. API changes

- `POST /delivery/assignments` (`manualAssign`): unchanged behavior, now also stamps
  `autoAssigned=false` explicitly (was already the default).
- `GET /delivery/assignments/search` and `GET /delivery/partners*`: responses gained
  `autoAssigned`/`partnerActiveDeliveries` and `activeDeliveries` respectively (live-computed, read
  only). `search` gained an optional `autoAssigned` query filter, used by the dashboard's KPI
  counts via the same `search().totalElements` pattern already used for per-status counts — **no
  new count/summary endpoint was added**.
- **No new public partner-selection endpoint** — `PartnerSelectionServiceImpl` stays an internal
  Spring service, called only from the Kafka consumer, per §22's explicit preference.
- Customer-facing APIs (`POST /orders`, `POST /orders/checkout`) are completely unchanged — they
  never accepted a `routeId`/`partnerId` before this task and still don't.

## 11. Database changes

One migration: `delivery-service/V8__add_auto_assigned_flag.sql` — adds
`delivery_assignments.auto_assigned BOOLEAN NOT NULL DEFAULT false`. Nothing else — workload is
computed live (§5), never persisted. No old migration was edited.

## 12. Frontend changes

Touched exactly the five screens §24 named, nothing else:

- **`AssignDeliveryDialog.tsx`**: now checks for an existing assignment as soon as an order is
  selected (`GET /delivery/assignments/order/{id}`) and, if one exists, shows a read-only "This
  order is already assigned" block (Partner / Route / Status / Automatic-or-Manual chip /
  partner's active-deliveries count) instead of a form that would just be rejected server-side
  (§25). The partner picker (shown only when no assignment exists yet) now shows each candidate's
  live active-delivery count in its option subtitle.
- **`DeliveryDetailsPage.tsx`** / **`OrderDetailsPage.tsx`**: both gained an
  "Automatically Assigned"/"Manually Assigned" chip and the assigned partner's live active-delivery
  count, sourced from the same `autoAssigned`/`partnerActiveDeliveries` fields.
- **`DeliveryDashboardPage.tsx`**: gained an "Automatic Assignment: Enabled" badge, three new real
  KPI cards ("Orders Waiting for Assignment" — order-service's existing `pendingOrders`,
  "Automatically Assigned" / "Manually Assigned" — `search({autoAssigned})` counts), and an
  "Assigned By" column on the assignments table.
- **`MyDeliveriesPage.tsx`**: genuinely unchanged (confirmed in §1 - it already renders whatever
  the ownership-scoped search returns, automatic or manual).
- Customer-facing screens (Shop/Cart/Checkout) were **not touched** — they never showed partner
  information and still don't; the customer only ever sees the delivery route/area, unchanged from
  the prior task.

## 13. Authorization

No role sets changed anywhere. `manualAssign` stays FARM_MANAGER/SUPER_ADMIN-only. Automatic
assignment is entirely server-side/event-driven — the customer has no way to influence it (order
creation APIs never accepted a route or partner id, before or after this task). Delivery-partner
ownership scoping (`findAllByDeliveryPartnerId`, the existing 404-not-403 pattern on
`findByIdAndDeliveryPartnerId`) is untouched - automatic and manual assignments are the same
`DeliveryAssignment` rows, so the existing ownership checks apply identically to both without any
new code.

## 14. Tests

- **`PartnerSelectionServiceImplTest`** (new, 16 tests): null route, no partners, one partner,
  multiple partners with distinct workloads, terminal statuses excluded from the workload query,
  deterministic tie-break (including a stability-across-repeated-calls test), ineligible partners
  never candidates (proven via the locked repository query's own filter).
- **`OrderEventConsumerTest`** (new, 8 tests): correct order/route/partner/status/autoAssigned on
  the happy path, the order's own route is used verbatim (not a substitute - the exact bug this
  fix targets), no-eligible-partner leaves the order unassigned without throwing, no-route-on-order
  skips before even attempting partner selection, a deleted route is treated the same as no route,
  idempotent redelivery (existing assignment) skips without re-querying, non-`ORDER_CREATED` events
  are ignored, and an unexpected exception never propagates out of the listener.
- **`DeliveryAssignmentServiceImplTest`**: extended for the `autoAssigned` search filter; all
  existing `manualAssign`/`search`/etc. tests still pass unchanged (autoAssigned defaults to
  `false`, matching the pre-existing manual-only behavior).
- **`DeliveryPartnerServiceImplTest`**: extended with the new `assignmentRepository` mock dependency
  (workload computation); all existing tests unchanged otherwise.
- Full suites: **delivery-service 143/145 pass**, **order-service 102/104 pass** (all 4 non-passing
  are the pre-existing Docker/Testcontainers-gated tests, unrelated, zero regressions).
- **Concurrency**: the locking *design* is code-reviewed and reasoned through in §9; Mockito mocks
  cannot simulate real Postgres row-lock contention, and Testcontainers/Docker are unavailable in
  this dev environment, so the actual concurrent-transaction interleaving described in §9 is
  **CODE VERIFIED, not independently load-tested**. The section 29 live-verification plan itself
  only asks for *sequential* correctness (workload changing across successive orders), which was
  live-verified (§15).

## 15. Live verification

**LIVE API VERIFIED** (real services, real accounts, real Postgres — all test data cleaned up
afterward):
- Created a real North-route setup: 2 delivery partners (A, B) on `F2H-NORTH`, 3 real customer
  orders (all correctly resolved `deliveryRouteId` = North's id, reusing the prior task's
  route-selection).
- Manually assigned 2 orders to Partner A. `GET /delivery/partners/{A}` and `{B}` then correctly
  showed **`activeDeliveries: 2`** and **`activeDeliveries: 0`** respectively — the live workload
  calculation confirmed correct against real data.
- `AssignDeliveryDialog` on the 3rd (unassigned) order: partner dropdown correctly showed
  "Partner A ... 2 active" / "Partner B ... 0 active" (**BROWSER VERIFIED**, screenshot).
- `AssignDeliveryDialog` opened against an already-assigned order: correctly showed the read-only
  "This order is already assigned" block (Partner A / Farm2Home North (F2H-NORTH) / ASSIGNED /
  Manual / 2 active deliveries) with no way to create a duplicate (**BROWSER VERIFIED**,
  screenshot).
- Delivery Dashboard: "Orders Waiting for Assignment", "Automatically Assigned" (0, honestly - see
  §7/§16 below), "Manually Assigned" (2, matching the real assignments just created), and the
  "Assigned By: Manual" column all rendered correctly with real numbers (**BROWSER VERIFIED**,
  desktop 1600px, mobile 390px, light and dark — no horizontal overflow, no console errors).
- `DeliveryDetailsPage`/`OrderDetailsPage`: "Manually Assigned" chip + partner's active-deliveries
  count both rendered correctly (**BROWSER VERIFIED**, dark mode).

**KAFKA E2E: NOT VERIFIED.** No Kafka broker runs in this dev environment (confirmed: every
service's Kafka producer/consumer logs connection-refused warnings on every request — a
pre-existing, permanent environment condition, not something this task changed). The
`ORDER_CREATED → OrderEventConsumer → automatic partner selection → DELIVERY_ASSIGNED` trigger
chain described in §3 could not be exercised end-to-end live. What *was* independently confirmed
live: (a) `Order.deliveryRouteId` is correctly populated at order-creation time (reused from the
prior task, re-confirmed here), and (b) the workload/eligibility data
`PartnerSelectionServiceImpl` depends on is correct against the real database (above). The
consumer's own logic is thoroughly unit-tested (§14) against a mocked `OrderEvent`, which proves
the code is correct once Spring Kafka would hand it a real deserialized event - it does not prove
message delivery, deserialization, or consumer-group behavior, none of which could be exercised
without a broker.

**Notification E2E: NOT VERIFIED**, for the same reason (Kafka down) - `DELIVERY_ASSIGNED`'s
actual delivery to notification-service was not observed live in this task. It was already
established as working (see §1/§8) from prior session work reusing the identical `manualAssign()`
code path, which itself has not been re-verified live in *this* task either - only reused as-is.

## 16. Known limitations

- Automatic assignment only fires on `ORDER_CREATED`. If an order's address/route legitimately
  changes before any assignment exists, this task did not add a "retry automatic assignment" path
  for that order - manual assignment remains the only way to assign it (matches §20's explicit "do
  not invent a new address-change workflow" instruction).
- No scheduler/retry job re-attempts automatic assignment for orders that were skipped because no
  partner was available at order-creation time (§17 explicitly said not to add one unless
  necessary; manual assignment is the existing, sufficient fallback).
- Kafka being down in this dev environment means automatic assignment cannot actually run at all
  right now - every order created in this environment today lands as "unassigned" until manually
  assigned, regardless of how correct the automatic logic is. This is an environment limitation,
  not a code limitation.

## 17. Future improvements

- A real Kafka broker in this environment would let §15's full live E2E plan (place an order,
  observe the automatic assignment, verify workload shifts across successive orders) actually run
  - everything needed for it is already built and unit-tested.
- A route-coverage-aware partner-count check (e.g. warn an admin when a route has zero active
  partners) could reuse the route-coverage-gap logging pattern from the prior task's
  `DeliveryRouteSelectionServiceImpl`, but wasn't requested here and would need a real trigger
  (scheduled job or an admin-facing endpoint) to be useful.
- If partner auto-assignment needs to consider proximity/vehicle-capacity/shift-hours in the
  future, `PartnerSelectionServiceImpl` is the single place that logic would extend from - it's
  already isolated behind one method signature (`selectLeastLoadedPartner(routeId)`) for exactly
  this reason.

---

## Final Status

| Item | Status |
|---|---|
| Route Selection | 🟢 (unchanged, reused from the prior task) |
| Automatic Partner Selection | 🟢 (implemented, unit-tested, live data dependencies verified) |
| Assignment Creation | 🟢 (correct order/route/partner/status, duplicate-protected) |
| Notification | 🟡 (reuses the existing, already-built DELIVERY_ASSIGNED path - not re-verified live in this task, Kafka down) |
| Kafka E2E | 🔴 NOT VERIFIED (no broker in this dev environment) |
| Manual Fallback | 🟢 (unchanged, still fully functional, now also shows automatic-assignment status) |
| Customer Security | 🟢 (no route/partner/assignment field ever accepted from the customer) |
| Partner Security | 🟢 (unchanged ownership scoping, applies identically to automatic and manual assignments) |
| Frontend | 🟢 (5 screens updated, desktop/mobile/light/dark verified, no console errors) |
| Tests | 🟢 (143/145 delivery-service, 102/104 order-service - all non-passing are pre-existing Docker-gated, zero regressions) |
