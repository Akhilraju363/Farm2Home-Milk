# Push Notification Support

notification-service now dispatches push notifications alongside SMS and EMAIL, through the same
provider-abstraction pattern used for [SMS](SMS_PROVIDER_INTEGRATION.md) and
[the payment gateway](PAYMENT_GATEWAY_INTEGRATION.md). This document covers the abstraction, the
four supported categories, notification history, and how events reach notification-service.

## Provider abstraction

`PushProvider` (`shared-libs/common-core/src/main/java/com/farm2home/common/core/push/PushProvider.java`)
mirrors `SmsProvider` exactly:

```
PushProvider
├── getName()               → String            (diagnostics/audit — "LOGGING", future: "FCM", ...)
└── send(PushMessage)       → PushSendResult     (one push attempt)
```

`PushService` — the single entry point every caller uses — wraps the active provider with bounded
retries, graceful failure handling, and audit publishing:

```
PushService.sendPush(recipient, title, body, eventType) → PushSendResult
```

- **Retries**: up to `push.max-attempts` (default 3), linear backoff (`push.retry-backoff-millis`
  × attempt number, default 150ms base) — identical policy shape to SMS.
- **Never throws**: a failure, even after every retry is exhausted, is reported via the returned
  `PushSendResult`, not an exception — a transient push gateway outage must never fail the
  business operation that triggered it.
- **Audits every send**: one audit entry per `sendPush()` call — `AuditAction.PUSH_SENT` or
  `PUSH_FAILED`, `entityType="Push"`, queryable independently of whatever higher-level business
  audit the caller (here, `NotificationServiceImpl`) also records.

`LoggingPushProvider` is the default (`push.provider` unset, or explicitly `logging`) — logs the
notification instead of sending it and always succeeds, so no real push gateway credentials are
needed for local development or any automated test. A future real provider (Firebase Cloud
Messaging, APNs, OneSignal, ...) plugs in exactly the way Razorpay did for payments: implement
`PushProvider`, add a `@ConditionalOnProperty(name = "push.provider", havingValue = "...")`-gated
`@Bean`, set `PUSH_PROVIDER` in the deploying environment. No other code changes.

## Why push is addressed by customer id, not a device token

Unlike SMS (mobile number) or EMAIL (email address), this codebase has no device-token registry —
customers never register a device with the backend. Push is instead addressed by **customer id**
(`event.getCustomerId().toString()`), which every notification event already carries and which
`NotificationServiceImpl.process()` already validates is non-null before dispatching anything. In
practice this maps naturally onto topic-based push messaging (e.g. Firebase Cloud Messaging
topics: the app subscribes each device to `customer-{customerId}` client-side, and the backend
publishes to that topic — no server-side device bookkeeping required) and means push is, if
anything, *more* reliably addressed today than SMS/EMAIL: those depend on the triggering event
actually carrying a `recipientMobile`/`recipientEmail`, which not every producer populates.

## The four supported categories

| Category | Event type(s) | Published by |
|---|---|---|
| Order Updates | `ORDER_CREATED` | order-service (existing) |
| Delivery Status | `DELIVERY_ASSIGNED`, `DELIVERY_COMPLETED`, `DELIVERY_DELAYED` | delivery-service (existing) |
| Payment Confirmation | `PAYMENT_SUCCESS` | payment-service (existing) |
| Subscription Alerts | `SUBSCRIPTION_CREATED`, `SUBSCRIPTION_PAUSED`, `SUBSCRIPTION_RESUMED`, `SUBSCRIPTION_CANCELLED` | subscription-service (**new** — see below) |

Each maps to a `notification_templates` row with `channel='PUSH'` and template code
`{eventType}_PUSH` (`V5__push_notification_templates.sql`); the row's `subject` doubles as the
push notification's title, `body` as its message, both rendered with the same `{{placeholder}}`
substitution SMS/EMAIL already use (`NotificationServiceImpl.render()`).

### Subscription Alerts required a new Kafka producer

`subscription.events` was consumed by both order-service (subscription snapshot sync) and
notification-service already, but **subscription-service had never actually published to it** —
confirmed by the topic's own prior javadoc and a comment on
`SubscriptionServiceImpl.checkUpcomingRenewals()`, both of which explicitly noted the missing
producer. This is now fixed: `SubscriptionEventProducer` (new,
`subscription-service/.../kafka/SubscriptionEventProducer.java`) publishes on the four lifecycle
actions the service already implements (`create`/`cancel`/`pause`/`resume`), using a shared
`SubscriptionEvent` class (`common-events`) whose field shape matches what order-service's
existing consumer already expected. subscription-service's own scheduled renewal-window check
(`checkUpcomingRenewals()`) remains ops-visibility-only (log-based) rather than customer-facing —
it's a forward-looking warning, deliberately kept distinct from the four already-implemented
lifecycle events above.

## Notification history

Every dispatched notification — SMS, EMAIL, or PUSH, sent or failed — is persisted to
`notification.notification_logs` exactly as before; PUSH added no new table or columns, just a
third `channel` value already defined on `NotificationChannel`. Read-only history API
(`NotificationLogController`, `GET /api/v1/notifications/*` — see Swagger UI, tag
"Notifications"):

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/notifications/logs?recipientId=...` | One recipient's full history across every channel |
| `GET /api/v1/notifications/logs/{id}` | A single log entry |
| `GET /api/v1/notifications/summary?limit=...` | Most recent entries across every recipient, for a dashboard feed |

There is no send-notification REST endpoint — dispatch is entirely event-driven (see below), by
design: every notification is a reaction to something that already happened elsewhere in the
platform, never a standalone action a client requests directly.

## Event flow (existing Kafka infrastructure)

No new topics were introduced. `NotificationEventConsumer` already listened to
`order.events`/`delivery.events`/`payment.events`/`subscription.events` (among others) and
deserializes every message into the same generic `KafkaEventDto`; `NotificationServiceImpl.process()`
now dispatches all three channels (SMS, EMAIL, PUSH) for any event that has a `customerId` and a
matching active template, instead of just SMS/EMAIL as before:

```
producer service → Kafka topic → NotificationEventConsumer → NotificationServiceImpl.process()
                                                                 ├── sendForChannel(SMS)   — needs recipientMobile
                                                                 ├── sendForChannel(EMAIL) — needs recipientEmail
                                                                 └── sendForChannel(PUSH)  — needs customerId (always present)
```

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `PUSH_PROVIDER` | `logging` | `logging` or a future real provider's own property value |
| `PUSH_MAX_ATTEMPTS` | `3` | Total send attempts (including the first) before giving up |
| `PUSH_RETRY_BACKOFF_MILLIS` | `150` | Base linear backoff between attempts, in milliseconds |

Set in notification-service's `application.yml`/`application-docker.yml` and `.env.example` at
the repo root — no secrets, since the logging provider needs none; a real provider's own
credential variables (e.g. an FCM service-account key) would be added alongside these the same way
`RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` were for payments.

subscription-service also gained `spring.kafka.bootstrap-servers` (previously absent — it had
never produced to Kafka) and a `spring-kafka` + `common-events` dependency.
