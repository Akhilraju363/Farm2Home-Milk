# SMS Provider Integration

SMS sending (OTP, order confirmation, delivery updates, payment success) goes through a shared
provider abstraction in `common-core` rather than each service logging or implementing delivery
itself. This document covers the abstraction, where it's used, and how to configure or extend it.

## Provider abstraction

`SmsProvider` (`shared-libs/common-core/src/main/java/com/farm2home/common/core/sms/SmsProvider.java`):

```
SmsProvider
├── getName()               → String            (diagnostics/audit — "LOGGING", future: "TWILIO", ...)
└── send(SmsMessage)        → SmsSendResult      (one SMS attempt)
```

`SmsService` — the single entry point every service actually calls — wraps the active provider
with bounded retries, graceful failure handling, and audit publishing:

```
SmsService.sendSms(to, body, eventType) → SmsSendResult
```

- **Retries**: up to `sms.max-attempts` (default 3) attempts, linear backoff
  (`sms.retry-backoff-millis` × attempt number, default 150ms base).
- **Never throws**: a failure — even after every retry is exhausted — is reported via the
  returned `SmsSendResult`, not an exception. A transient SMS outage must never fail the business
  operation that triggered it (an OTP row is already persisted and usable regardless of whether
  the SMS arrived; an order is still confirmed even if the receipt SMS didn't go out).
- **Audits every send**: exactly one audit entry per `sendSms()` call (not per retry attempt) —
  `AuditAction.SMS_SENT` or `AuditAction.SMS_FAILED`, `entityType="Sms"`, `entityId`/`username`
  set to the recipient number, `details` including the event type, provider name, and attempt
  count.

This mirrors payment-service's `PaymentGatewayProvider`/`PaymentServiceImpl` split from the
Razorpay integration: a thin provider interface for "how to physically send," and a
provider-agnostic wrapper for retry/failure/audit policy.

## Where it's used

| Caller | Service | Event type | Notes |
|---|---|---|---|
| `OtpService.generateAndSend()` | auth-service | `OTP` | Direct, synchronous — the OTP row is already persisted before the SMS is attempted |
| `NotificationServiceImpl.dispatch()` (SMS channel) | notification-service | `ORDER_CREATED`, `DELIVERY_ASSIGNED`, `DELIVERY_COMPLETED`, `PAYMENT_SUCCESS`, ... | Driven by the existing Kafka event → template pipeline; any event with an active `*_SMS` template is sent |

Both callers depend only on `SmsService` (constructor-injected, auto-configured — no per-service
wiring needed since `common-core`'s `SmsAutoConfiguration` registers the beans for every service
that depends on common-core and has JPA on the classpath).

### Why OTP doesn't go through notification-service's Kafka pipeline

OTP delivery is latency-sensitive (the user is waiting for the code to complete login/
registration) and auth-service already generates the code synchronously in the same request — so
`OtpService` calls `SmsService` directly rather than publishing a Kafka event and waiting for
notification-service to pick it up. This was already the design before this change (see
`OtpEventProducer`, used only for the best-effort email channel); only the SMS *transport* changed
(plain `log.info` → `SmsProvider`), not this data flow.

### Avoiding duplicate audit entries in notification-service

`NotificationServiceImpl` already writes its own audit entry per channel dispatch (entityType
`Notification`, tied to the `NotificationLog` row). Since `SmsService` now audits every SMS send
itself, `NotificationServiceImpl` skips its own audit call specifically for the SMS channel
(EMAIL and PUSH are unaffected) — recording it twice under two different entity types would just
be the same information duplicated.

## Development provider

`LoggingSmsProvider` is the default (`sms.provider` unset, or explicitly `logging`) — it logs the
message instead of sending it and always succeeds, so no real SMS gateway credentials are needed
for local development or any automated test. To exercise `SmsService`'s retry/failure path in a
test, provide a mock/stub `SmsProvider` instead of relying on this one.

## Adding a real provider

Configuration-driven, following the exact same shape as payment-service's Razorpay integration:

1. Implement `SmsProvider` (e.g. `TwilioSmsProvider`), defining whatever
   `@ConfigurationProperties` it needs for its own credentials (API key, sender id, etc.) —
   following `RazorpayProperties`' pattern of naming secret fields so Spring Boot Actuator's
   default `Sanitizer` masks them automatically, plus `@ToString.Exclude`.
2. Register it as a `@Bean` gated by
   `@ConditionalOnProperty(name = "sms.provider", havingValue = "twilio")` — either directly in
   `SmsAutoConfiguration` (if it should be available to every service) or in the one service that
   needs it.
3. Set `SMS_PROVIDER=twilio` (plus that provider's own credential env vars) in the deploying
   environment.

No change is needed in `SmsService`, `OtpService`, `NotificationServiceImpl`, or their tests —
they depend only on the `SmsProvider`/`SmsService` abstraction, never a concrete provider.
`LoggingSmsProvider`'s own `@Bean` backs off automatically (`@ConditionalOnMissingBean`) once
another `SmsProvider` bean is present, so a service can also just define its own without touching
the `sms.provider` property at all.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `SMS_PROVIDER` | `logging` | `logging` or a future real provider's own property value |
| `SMS_MAX_ATTEMPTS` | `3` | Total send attempts (including the first) before giving up |
| `SMS_RETRY_BACKOFF_MILLIS` | `150` | Base linear backoff between attempts, in milliseconds |

Set in auth-service's and notification-service's `application.yml`/`application-docker.yml`
(the two services that currently send SMS) and `.env.example` at the repo root — no secrets here,
since the logging provider needs none; a real provider's own credential variables would be added
alongside these the same way `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` were for payments.

## Audit trail

Two new `AuditAction` constants (`shared-libs/common-core/.../audit/AuditAction.java`):
`SMS_SENT` and `SMS_FAILED`. Every `SmsService.sendSms()` call writes exactly one audit row
regardless of caller, queryable by `entityType="Sms"` across any service's `audit_log` table —
independent of whatever higher-level business audit (OTP generation, notification delivery) the
caller also records.
