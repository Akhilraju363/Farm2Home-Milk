# Email Notifications

Email is a channel inside the existing notification-service, not a separate service. This
document covers the architecture, configuration, and operational behavior added/hardened for the
"implement email notifications end-to-end" task.

## 1. Architecture

```
Order/Payment/Delivery/Subscription/Auth-service
        |
        | Kafka domain event (order.events / payment.events / delivery.events /
        |                      subscription.events / customer.events / otp.events)
        v
notification-service (NotificationEventConsumer -> NotificationServiceImpl.process())
        |
        +---- SMS    (SmsService, common-core)
        |
        +---- PUSH   (PushService, common-core)
        |
        +---- EMAIL  (EmailService, local to notification-service)
                |
                v
          EmailProvider
           /          \
   LoggingEmailProvider   SmtpEmailProvider
   (default, dev-safe)    (EMAIL_ENABLED=true)
                                |
                                v
                          Mailtrap / real SMTP
```

Every business service (order/payment/delivery/subscription/auth) publishes a domain event to
Kafka and knows nothing about email, SMS, or push. `NotificationServiceImpl.process()` is the one
place that resolves a template, renders it, and dispatches to whichever channels have both a
recipient value and an active template for that event. This was already true for SMS/PUSH before
this task; EMAIL now follows the identical shape.

**No business service ever calls an email provider directly**, and no new HTTP dependency was
added between any two services - email stays entirely inside the existing event-driven pipeline.

## 2. Email provider

`EmailProvider` (interface, `notification-service/.../email/`) mirrors `SmsProvider`/
`PushProvider` (common-core) exactly: `getName()` for diagnostics, `send(EmailMessage)` returning
an `EmailSendResult` rather than throwing where possible.

It lives inside notification-service rather than common-core because - unlike SMS, which
auth-service (OTP) and notification-service both send - email is only ever sent by
notification-service today. This matches how `PaymentGatewayProvider` (Razorpay/mock) lives inside
payment-service rather than a shared library, since only payment-service needs it.

Two implementations, selected by `EmailConfig` based on `email.enabled`/`email.provider`:

- **`LoggingEmailProvider`** - logs the email instead of sending it. Active whenever
  `EMAIL_ENABLED=false` (the default), regardless of `EMAIL_PROVIDER`. Always succeeds. Used by
  every automated test and by default in local dev/CI - no SMTP credentials required.
- **`SmtpEmailProvider`** - sends via Spring's `JavaMailSender` (itself bound from
  `spring.mail.*` / `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM`, unchanged
  from before this task). Active only when `EMAIL_ENABLED=true` and `EMAIL_PROVIDER=smtp`. Every
  failure path (mail sender absent, SMTP exception) returns a failed `EmailSendResult` rather than
  throwing - the retry/audit layer above it treats both identically.

`EmailService` wraps whichever provider is active with **bounded retry + audit**, mirroring
`SmsService` line for line: up to `EMAIL_MAX_ATTEMPTS` attempts (default 3), linear backoff of
`EMAIL_RETRY_BACKOFF_MILLIS * attempt` milliseconds (default 150ms) between attempts, and exactly
one audit entry per call (`AuditAction.EMAIL_SENT`/`EMAIL_FAILED`) recording the final outcome and
attempt count. `EmailService.sendEmail()` never throws - a failure after every retry is reported
via the returned result, never an exception, so a transient SMTP outage can never fail the
order/payment/delivery operation that triggered the notification (that operation already committed
before notification-service even received the Kafka event).

This is a genuine hardening over what existed before: previously, `NotificationServiceImpl` called
`JavaMailSender` directly with a single attempt and no dedicated audit trail entry per email
outcome (the audit block that did exist was folded into `sendForChannel()` as a special case).
EMAIL now has the exact same reliability characteristics as SMS/PUSH.

## 3. Configuration

All environment variables (see `.env.example`, root and none needed in `frontend/.env.example` -
email has no frontend surface):

| Variable | Default | Meaning |
|---|---|---|
| `EMAIL_ENABLED` | `false` | Master switch. `false` always uses `LoggingEmailProvider`. |
| `EMAIL_PROVIDER` | `smtp` | Only real option today; irrelevant while disabled. |
| `EMAIL_FROM_NAME` | `Farm2Home` | Display name on the email "From" header. |
| `EMAIL_MAX_ATTEMPTS` | `3` | Total send attempts (including the first) before giving up. |
| `EMAIL_RETRY_BACKOFF_MILLIS` | `150` | Linear backoff base between attempts. |
| `MAIL_HOST` | `sandbox.smtp.mailtrap.io` | SMTP host (unchanged, pre-existing). |
| `MAIL_PORT` | `2525` | SMTP port (unchanged, pre-existing). |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | *(empty)* | SMTP credentials - **never commit real values**. |
| `MAIL_FROM` | `no-reply@farm2homemilk.example` | Email "From" address (unchanged, pre-existing). |

Bound in notification-service's `application.yml`/`application-docker.yml` under `email.*`
(`enabled`, `provider`, `from-name`, `max-attempts`, `retry-backoff-millis`) via
`EmailProperties`/`EmailConfig`. `spring.mail.*` and `app.mail.from` are unchanged from before this
task - only the provider-selection/retry layer is new.

**Behavior change from before this task**: previously, email had no `EMAIL_ENABLED` gate at all -
`MAIL_HOST` defaulted to Mailtrap's sandbox host, so every event attempted a real (unauthenticated,
likely-failing) SMTP handshake by default. `EMAIL_ENABLED` now defaults to `false`, matching
`SMS_PROVIDER=logging`/`PUSH_PROVIDER=logging`'s existing "safe by default" convention - local dev
and CI no longer attempt any SMTP connection unless explicitly opted in.

## 4. Local development / Mailtrap setup

1. Leave `EMAIL_ENABLED=false` (default) to see every email logged to the console instead of sent
   - nothing else to configure.
2. To actually receive test emails: sign up at [mailtrap.io](https://mailtrap.io) (free), open your
   sandbox inbox's SMTP settings, and set in your local `.env` (never committed):
   ```
   EMAIL_ENABLED=true
   MAIL_USERNAME=<your sandbox inbox username>
   MAIL_PASSWORD=<your sandbox inbox password>
   ```
3. To use Gmail SMTP instead of Mailtrap: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, and an
   [App Password](https://myaccount.google.com/apppasswords) (not your regular password) for
   `MAIL_PASSWORD`.
4. Restart notification-service after changing any of these - they're read once at startup.

## 5. Supported notification types

Reused the existing notification-template system entirely - no new templating engine, no
hardcoded HTML in Java code. Every EMAIL template is a row in `notification.notification_templates`
(`channel='EMAIL'`), resolved by `{eventType}_EMAIL` and rendered with `{{placeholder}}`
substitution, exactly like SMS/PUSH.

| Event | Real producer | EMAIL template | Added/changed by this task |
|---|---|---|---|
| `CUSTOMER_CREATED` | auth-service (registration) | ✅ | pre-existing |
| `OTP` | auth-service (OTP generation) | ✅ | pre-existing |
| `ORDER_CREATED` | order-service | ✅ | pre-existing |
| `PAYMENT_SUCCESS` | payment-service | ✅ | pre-existing |
| `PAYMENT_FAILED` | payment-service | ✅ | HTML layout standardized (was plain text) |
| `PAYMENT_REFUNDED` | payment-service | ✅ | **new** - had zero templates on any channel before |
| `DELIVERY_ASSIGNED` | delivery-service | ✅ | HTML layout standardized |
| `DELIVERY_OUT_FOR_DELIVERY` | delivery-service | ✅ | HTML layout standardized |
| `DELIVERY_COMPLETED` | delivery-service | ✅ | HTML layout standardized |
| `DELIVERY_FAILED` | delivery-service | ✅ | HTML layout standardized |
| `DELIVERY_DELAYED` | delivery-service | ✅ | pre-existing |
| `SUBSCRIPTION_CREATED` | subscription-service | ✅ | HTML layout standardized |
| `SUBSCRIPTION_PAUSED` | subscription-service | ✅ | HTML layout standardized |
| `SUBSCRIPTION_RESUMED` | subscription-service | ✅ | HTML layout standardized |
| `SUBSCRIPTION_CANCELLED` | subscription-service | ✅ | HTML layout standardized |

**NOT AVAILABLE** (no real producer/event exists in the backend today - not built, not faked):

- `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `ORDER_DELIVERED` - order-service publishes only
  `ORDER_CREATED`. Order lifecycle beyond creation is communicated via the `DELIVERY_*` events
  above (`DELIVERY_ASSIGNED` ≈ order confirmed for delivery, `DELIVERY_COMPLETED` ≈ order
  delivered), which already have EMAIL templates.
- `ACCOUNT_VERIFICATION`, `PASSWORD_RESET` as distinct types - auth-service publishes a single
  `OTP` event for registration, login, and forgot-password alike (`OtpType` distinguishes the
  *purpose* internally but the Kafka event and its template are shared). Splitting these into
  distinct notification types would require a backend change to `OtpEventProducer` this task did
  not make, since it's outside "implement the email channel."
- `INVOICE_GENERATED` - invoice-service has no Kafka producer of any kind; invoices are generated
  synchronously via its REST API with no event published.

## 6. Recipient resolution

Unchanged from the pre-existing SMS/PUSH pattern - not new code, described here for completeness.
`NotificationServiceImpl.process()` uses `KafkaEventDto.recipientEmail` if the producing event
already carries it (`CustomerEvent`, `OtpEvent` both have an `email` field), otherwise it looks up
the customer's contact info via `CustomerServiceClient` (`customer-service`'s own `GET
/customers/{id}/contact`-equivalent internal endpoint) - the same cross-service pattern already
used for SMS. A lookup failure just means that event falls back to whichever channels don't need
enrichment (typically PUSH, which addresses by customerId).

**The recipient email is never supplied by an untrusted client request.** `KafkaEventDto` is bound
exclusively from Kafka message payloads inside `NotificationEventConsumer.consume()`, a
`@KafkaListener` - there is no controller endpoint anywhere that accepts a recipient address and
forwards it to `EmailService`. A customer cannot trigger an email to an arbitrary address; the
recipient is always derived from server-side state (the authenticated actor's own profile, or the
`customerId` on a domain event that itself came from server-side order/payment/delivery logic).

## 7. Consent / DPDP

Every email type listed in §5 is **transactional** (order confirmation, payment status, delivery
status, refund, subscription lifecycle, OTP/account security, welcome). Per the existing consent
model (`ConsentPurpose.ESSENTIAL_SERVICE` in customer-service), transactional communication
necessary to operate the account/order the customer already initiated does not require a
marketing-style opt-in gate - DPDP Act 2023 §6 treats this as within the contract/legitimate-use
basis, and the existing consent screens already record `ESSENTIAL_SERVICE` as an acknowledgment
rather than a genuine opt-in toggle for exactly this reason.

**No marketing email exists in this codebase** - there is no newsletter/promotional template, no
producer that would trigger one. This task did not add one. If a marketing email is ever built, it
**must** gate on `ConsentPurpose.MARKETING_COMMUNICATIONS` (already defined, already a genuine
opt-in per the existing `ConsentRecord`/`ConsentService`) before sending - documented here as a
requirement for that future work, not implemented now, since there is nothing to gate.

See `DPDP_PROGRESS.md` for the platform-wide consent model this reasoning is built on.

## 8. Retry behavior

`EmailService` retries **within a single Kafka message's processing**, exactly like `SmsService`:
up to `EMAIL_MAX_ATTEMPTS` attempts with linear backoff, all synchronous, all before
`NotificationEventConsumer.consume()` returns. There is no separate scheduled job that re-attempts
a previously-FAILED notification_logs row later - this mirrors the pre-existing SMS/PUSH behavior
exactly (an intentional decision, not an oversight: the task's own instructions were not to build a
new queue/scheduler when the existing bounded in-call retry already meets "no infinite loop,
backoff, clear failure logging").

If every attempt fails, the row is logged `FAILED` with the reason, an `EMAIL_FAILED` audit entry
is recorded, and the business operation that produced the event (order creation, payment,
assignment) is entirely unaffected - it already succeeded before notification-service ever saw the
Kafka event.

## 9. Idempotency

A Kafka redelivery (consumer restart or rebalance before the offset commits) would otherwise
re-run `NotificationServiceImpl.process()` for the same message and double-send. `sendForChannel()`
now checks, before attempting any send, whether a `SENT` log already exists for
`(channel, eventType, sourceEventId, eventOccurredAt)` - the last two are new columns on
`notification_logs` (migration `V9`).

- `sourceEventId` is the producing event's own entity id (`orderId`/`paymentId`/`assignmentId`/
  `subscriptionId` - see `KafkaEventDto.getDedupeEntityId()`).
- `eventOccurredAt` is when the source event was published (every producer already sets this).

**Both together**, not the entity id alone, because several event types can legitimately recur for
the same entity: a subscription can be paused, resumed, then paused again (same `subscriptionId`,
same `eventType`, genuinely two different real occurrences); a delivery can be delayed more than
once. Keying on entity id alone would have silently suppressed the second, real notification.
Keying on `(entityId, occurredAt)` correctly treats a true redelivery (identical timestamp) as a
duplicate while still sending a genuinely new occurrence (a new timestamp).

`OTP` and `CUSTOMER_CREATED` carry no entity id at all and are **not deduped** - documented
decision, not a gap: OTP resends are intentional (a user can request a new code) and must never be
silently dropped, and `CUSTOMER_CREATED` fires exactly once per customer in practice.

A partial unique index (`idx_notif_logs_dedupe`, `V9`) enforces this at the database level -
`WHERE status = 'SENT' AND source_event_id IS NOT NULL AND event_occurred_at IS NOT NULL` - so a
race between two concurrent redeliveries is caught by the database even if both passed the
application-level check. `NotificationServiceImpl` catches the resulting
`DataIntegrityViolationException` and logs it rather than propagating (the send itself, if it
happened, can't be un-sent - this only prevents a duplicate history row).

## 10. Database migrations

- `V9__email_delivery_hardening.sql` - adds `source_event_id`/`event_occurred_at` columns, the
  partial unique index above, and the `PAYMENT_REFUNDED_EMAIL` template (a real event -
  `payment.events`, `PaymentServiceImpl.refund()` - that had **zero** template on any channel
  before this task, not just EMAIL).
- `V10__standardize_email_template_layout.sql` - upgrades every remaining plain-text EMAIL
  template (added in earlier `V6`/`V8` migrations) to the same branded HTML layout `V3` established
  for the first batch, so every email in the notification center now renders consistently.

Both are new files; no previously-applied migration was edited. `PAYMENT_REFUNDED_EMAIL`'s SMS/PUSH
counterparts remain missing (pre-existing gap, out of scope for an "email notifications" task -
noted here rather than silently left unaddressed).

## 11. Frontend

**No frontend changes were needed.** `NotificationChannel` (`'SMS' | 'EMAIL' | 'PUSH'`), the
notification bell's channel icon mapping (`TopBar.tsx`), and every notification-history screen
already handled `EMAIL` as a first-class channel value before this task - confirmed by audit, not
assumed. There is no "send email" control anywhere in the UI (email is entirely system-triggered,
per the architecture in §1) and none was added. There is no notification-preferences screen in this
codebase to integrate into (see §12).

## 12. Notification preferences / admin template management

Audited, not built (per this task's explicit "don't build a large system unless required"
instruction):

- **No per-user notification-channel preferences exist** (no "disable email" toggle anywhere).
  Transactional notifications remain always-on for every channel that has a resolvable recipient
  and an active template - unchanged behavior, same as SMS/PUSH already had. Documented as a
  deliberate scope decision, not an oversight.
- **No admin template-management UI/API exists** - templates are Flyway-seeded data only,
  administered by writing a migration (see §10), exactly as SMS/PUSH templates already were before
  this task. `NotificationLogController` only ever exposed read-only delivery *history*, never
  template CRUD; this task did not change that.

## 13. Troubleshooting

- **Emails aren't arriving, but nothing looks wrong in the logs** - check `EMAIL_ENABLED`. If
  `false` (the default), every email is only logged (`[EMAIL] To: ... | Event: ... | Subject:
  ...`), never sent - this is correct behavior, not a bug.
- **`SMTP connection refused` / `535 Authentication failed` in `notification_logs.failure_reason`**
  - `EMAIL_ENABLED=true` but `MAIL_USERNAME`/`MAIL_PASSWORD` are blank or wrong for the configured
    `MAIL_HOST`. Business operations are unaffected either way (see §8) - fix the credentials and
    the next real event will send correctly; nothing needs to be manually replayed.
- **"Why didn't this event send an email at all?"** - check whether an active
  `{eventType}_EMAIL` template exists in `notification.notification_templates`. A missing/inactive
  template is a silent no-op for that channel only (SMS/PUSH for the same event are unaffected) -
  this was true before this task and remains true.
- **A notification looks like a duplicate** - check whether it's actually the *same* occurrence
  (identical `eventType` + entity id + `event_occurred_at` - true duplicate, should not happen; if
  it does, check for a Kafka consumer-group misconfiguration) versus a *new*, legitimate occurrence
  of a recurring event type (different `event_occurred_at` - not a bug, see §9).
