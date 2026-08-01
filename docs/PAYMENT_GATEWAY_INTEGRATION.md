# Payment Gateway Integration

`payment-service` processes real online payments (UPI/card, routed through Razorpay) alongside
the pre-existing in-app Wallet and Cash-on-Delivery methods. This document covers the provider
abstraction, the request flows, environment configuration, and how to test locally without a
real Razorpay account.

## Provider abstraction

All gateway-specific logic sits behind one interface, `PaymentGatewayProvider`
(`payment-service/src/main/java/com/farm2home/payment/gateway/PaymentGatewayProvider.java`).
`PaymentServiceImpl` depends only on this interface — it has no Razorpay-specific code anywhere.

```
PaymentGatewayProvider
├── createOrder(...)        → GatewayOrder            (start a payment attempt)
├── verifyPayment(...)      → GatewayPaymentVerification (client-reported checkout completion)
├── refund(...)             → GatewayRefund            (reverse a captured payment)
├── fetchPaymentStatus(...) → GatewayPaymentStatus     (look up by known gateway payment id)
├── fetchOrderStatus(...)   → GatewayPaymentStatus     (look up by gateway order id — no payment id needed yet)
└── parseWebhookEvent(...)  → GatewayWebhookEvent      (verify + decode an inbound webhook)
```

Two implementations exist:

| Provider | Package | When active | External calls |
|---|---|---|---|
| **Mock** (default) | `gateway.mock` | `payment.gateway.provider=mock` (or unset) | None — synthetic ids, deterministic verify() outcome |
| **Razorpay** | `gateway.razorpay` | `payment.gateway.provider=razorpay` | HTTPS to `api.razorpay.com` |

The active provider is selected by `PaymentGatewayConfig` via `@ConditionalOnProperty`, driven
entirely by environment variables — see [Configuration](#configuration) below. Adding a second
real provider (e.g. Stripe) means adding one more `PaymentGatewayProvider` implementation and one
more `@ConditionalOnProperty` bean; nothing in `PaymentServiceImpl`, the controller, or the tests
that exercise business rules needs to change.

### Mock provider

Used by every automated test and local development by default — no credentials required, no
network calls. It never auto-resolves a payment on its own (`fetchPaymentStatus`/
`fetchOrderStatus` always report `UNKNOWN`), so the reconciliation job is a harmless no-op under
mock, matching the original pre-gateway behavior where a PENDING payment only ever moved via a
manual callback. `verifyPayment()` only reports success for the exact signature
`MockPaymentGatewayProvider.MOCK_VALID_SIGNATURE` — anything else (including a blank string)
reports failure, so both branches are testable deterministically.

### Razorpay provider

A hand-rolled REST client (`RazorpayPaymentGatewayProvider`, over Spring WebFlux's `WebClient`,
HTTP Basic auth with the key id/secret pair) rather than Razorpay's Java SDK, keeping the
module's dependency footprint unchanged. Amounts convert to paise at the boundary
(`toSmallestUnit`); everywhere else in the service amounts stay in rupees. Checkout/webhook
signatures are HMAC-SHA256, verified with a constant-time comparison (`RazorpaySignature`,
`MessageDigest.isEqual`) to avoid leaking timing information about a partial match.

## Request flows

### Create payment (`POST /api/v1/payments`)

Unchanged for WALLET (instant debit + SUCCESS) and CASH (instant PENDING, resolved on delivery).
For UPI/RAZORPAY, `initiate()` now also calls `gatewayProvider.createOrder(...)` and stores the
returned `gatewayOrderId` on the `Payment` row before it's saved. The response carries a
`gatewayCheckoutKeyId` — the provider's **public** key id (Razorpay explicitly documents this as
safe to ship to a browser; it is never the key secret) — for a client-side checkout widget to
launch with. The payment stays `PENDING` until one of the following resolves it.

### Verify payment (`POST /api/v1/payments/{id}/verify`)

Called by the frontend right after the checkout widget reports a completed payment
(`gatewayOrderId`, `gatewayPaymentId`, `signature`). The provider verifies the signature and
(where supported) re-confirms the payment's status directly with the gateway rather than trusting
the client's claim — a forged or stale signature can never flip a payment to SUCCESS. Resolves to
SUCCESS or FAILED; unlike `initiate()`'s validation errors, an invalid signature is a normal
business outcome here (mirrors `processCallback`'s existing shape) — the endpoint returns 200 with
`paymentStatus: FAILED`, it does not throw.

### Webhook handling (`POST /api/v1/payments/webhook`)

Server-to-server notification from the gateway (Razorpay's `payment.captured`/`payment.failed`
events) — public (no bearer auth, since the gateway cannot attach our JWT) but every payload's
signature is verified before anything is acted on (`X-Razorpay-Signature` header, HMAC over the
raw request body with the separate webhook secret). Idempotent: gateways routinely retry webhook
delivery, and a payment already out of `PENDING` (resolved by `verify()`, a prior webhook, or a
manual callback) is left untouched.

### Refund (`POST /api/v1/payments/{id}/refund`, admin only)

Behavior now depends on how the original payment was collected:

- **WALLET** — credited straight back to the customer's in-app wallet (unchanged; the money never
  left the app in the first place).
- **UPI / RAZORPAY** — refunded through the gateway back to the original source (bank/UPI/card),
  via `gatewayProvider.refund(...)`. Requires a `gatewayPaymentId` to already be recorded on the
  payment; if one isn't (e.g. a payment created before this migration), the refund is rejected
  with a clear error rather than silently crediting the wallet — gateway-collected money should
  never be reversed by an in-app credit.
- **CASH** — status-only; no monetary instrument to reverse automatically.

This is a deliberate behavior change from the pre-gateway mock flow, where every non-CASH refund
credited the wallet regardless of payment method.

### Payment Status Synchronization

`PaymentReconciliationJob` (`@Scheduled`, every `payment.gateway.reconciliation.interval-ms`,
default 5 minutes) is the safety net for payments whose webhook was never delivered and whose
customer never returned to call `verify()` either — e.g. they paid successfully but closed the
tab before the checkout widget's callback fired. It finds PENDING gateway payments older than
`payment.gateway.reconciliation.stale-after-minutes` (default 5) and re-checks each one against
the gateway via `PaymentService.syncStatus(paymentId)`. `syncStatus` reuses the exact same
transition/event-publishing logic `verify()` and the webhook handler use (a single
`applyGatewayStatus` helper) — the job only decides *which* payments to check, it never
duplicates the status-transition rules. `syncStatus` is also exposed for manual/admin-triggered
resync if a support agent needs to force a re-check.

## Asynchronous event publishing

Every status-changing path (`initiate`'s WALLET branch, `verify`, `handleWebhook`,
`processCallback`, `refund`, `syncStatus`) publishes a Spring `PaymentStatusChangedEvent` via
`ApplicationEventPublisher` from inside its transaction, instead of calling the Kafka producer
directly. `PaymentEventListener` picks the event up via `@TransactionalEventListener(phase =
AFTER_COMMIT)` + `@Async("paymentEventExecutor")`:

- **After commit** — a payment whose transaction later rolls back never gets an event published
  for a change that didn't happen.
- **Off the request thread** — a slow Kafka broker never adds latency to the HTTP response; the
  dedicated `paymentEventExecutor` thread pool (`PaymentAsyncConfig`) is kept separate from
  `common-core`'s `auditTaskExecutor` so a burst of payment events can't starve audit-log writes.

`PaymentEventProducer` (the actual `KafkaTemplate` publish) is unchanged — only *when* and *on
which thread* it's called moved.

## Configuration

All gateway configuration is environment-variable-driven — nothing provider-specific is
hardcoded, and no secret is ever committed. See `.env.example` at the repo root.

| Variable | Default | Notes |
|---|---|---|
| `PAYMENT_GATEWAY_PROVIDER` | `mock` | `mock` or `razorpay` |
| `RAZORPAY_KEY_ID` | *(empty)* | Public — safe to expose to a browser checkout widget |
| `RAZORPAY_KEY_SECRET` | *(empty)* | **Secret** — used to sign/verify checkout & API requests |
| `RAZORPAY_WEBHOOK_SECRET` | *(empty)* | **Secret** — separate from the key secret; set when configuring the webhook in the Razorpay dashboard |
| `RAZORPAY_BASE_URL` | `https://api.razorpay.com/v1` | Override for testing against a sandbox/mock server |
| `RAZORPAY_CURRENCY` | `INR` | ISO currency code passed to Razorpay |
| `PAYMENT_RECONCILIATION_ENABLED` | `true` | Disable the scheduled job entirely if needed |
| `PAYMENT_RECONCILIATION_INTERVAL_MS` | `300000` (5 min) | How often the job runs |
| `PAYMENT_RECONCILIATION_STALE_MINUTES` | `5` | How old a PENDING payment must be before it's checked |

### Not exposing secrets

- `RazorpayProperties`' `keySecret`/`webhookSecret` fields are named to match Spring Boot
  Actuator's default `Sanitizer` key patterns (both contain "secret") — if `/actuator/env` is ever
  queried (it's exposed in this service's `management.endpoints.web.exposure.include` for
  legitimate diagnostics), both values are automatically masked as `******`. `@ToString.Exclude`
  covers the same two fields for direct logging/`toString()` calls that bypass Actuator entirely.
- `.env.example` ships only empty placeholders and the `mock` default — real credentials live in
  each environment's own `.env` (gitignored) or secret manager, never in source control.
- The only value returned to API clients is `gatewayCheckoutKeyId` — Razorpay's own documentation
  states the Key ID is meant to be public/client-side, analogous to Stripe's publishable key.

## Setting up a real Razorpay integration

1. Create a Razorpay account and, from the dashboard, go to **Settings → API Keys** to generate a
   test-mode key id/secret pair.
2. Set `PAYMENT_GATEWAY_PROVIDER=razorpay`, `RAZORPAY_KEY_ID`, and `RAZORPAY_KEY_SECRET` in your
   environment (or `.env` for Docker Compose).
3. In the dashboard, go to **Settings → Webhooks**, add
   `https://<your-domain>/api/v1/payments/webhook`, subscribe to `payment.captured` and
   `payment.failed`, and copy the generated webhook secret into `RAZORPAY_WEBHOOK_SECRET`.
4. Restart payment-service. `mvn -pl payment-service verify` and every other automated test
   continue to run against the mock provider regardless of this configuration — switching to
   `razorpay` only affects the running application, not the test suite.

## Database changes

`V4__add_payment_gateway_columns.sql` adds `gateway_order_id`, `gateway_payment_id`, and
`gateway_refund_id` (all nullable `VARCHAR(100)`, indexed) to `payment.payments`. Existing rows
are unaffected; a payment created before this migration simply has all three columns `NULL` (this
is why refund() explicitly rejects gateway refunds for payments with no recorded
`gatewayPaymentId`, rather than attempting a refund call the gateway would reject anyway).

## API surface changes

The `PaymentService` interface is unchanged for every pre-existing method
(`initiate`, `processCallback`, `refund`, `findById`, `findByOrderId`, `findAll`, `getSummary`,
`getReport`, `export`, `getRevenueTrend`, `getPaymentAnalytics`, `hasPayableProgress`) — all
existing callers keep working. Two methods were added: `verify(...)` and `handleWebhook(...)`,
plus `syncStatus(...)` for reconciliation/manual resync. `PaymentResponse` gained three optional
fields (`gatewayOrderId`, `gatewayPaymentId`, `gatewayCheckoutKeyId`) — additive, `null` when not
applicable, so existing consumers that don't know about them are unaffected.

`POST /payments/callback` (the original manual/legacy status-update endpoint) is kept exactly as
it was, for backward compatibility and as a way to simulate gateway outcomes under the mock
provider. Real gateway outcomes should arrive via `POST /{id}/verify` or `POST /webhook` instead.
