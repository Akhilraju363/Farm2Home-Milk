# DPDP Act 2023 Compliance — Progress Log

Branch: `compliance/dpdp` (not pushed). This file is the running decision log requested for this
work — updated as the audit and implementation progressed, not written after the fact.

---

## 1. Audit findings (before any code was written)

Full detail came from a codebase-wide read-only audit. Summary:

### 1.1 Personal data collection points
- **Registration** (`frontend/src/pages/auth/RegisterPage.tsx`): first/last name, mobile (required),
  email (optional), password; house/street/area/pincode + state/district/city; GPS
  latitude/longitude of the address (`CustomerAddress.latitude/longitude`); milk-type/quantity/
  delivery-slot preferences; mobile OTP.
- **Auth identity** (`auth-service` `User` entity): username, email, mobile, BCrypt password hash.
- **Profile photo** upload (`CustomerController POST /{id}/profile-image`).
- **Delivery partners** (`delivery-service`): name, mobile, vehicle type, plus a **continuous GPS
  location trail** (`DeliveryLocation`: lat/long/accuracy/speed/heading per ping during an active
  delivery) — this is location tracking of a human (the delivery partner), a distinct collection
  point from customer address GPS.
- **Payments**: no card/UPI/bank details stored — `payment-service` only persists gateway
  correlation IDs (Razorpay). Confirmed by reading `Payment.java`.
- **No KYC, biometric, or health-of-a-person data anywhere.** (`HealthRecordController`/
  `VaccinationController` in `farm-service` are veterinary records for cows, out of DPDP scope.)
- Every service writes to a generic, indefinitely-retained audit log (`AuditLogService`) capturing
  userId/username/IP/request details on most actions — itself a personal-data store with no purge
  job.

### 1.2 Trackers / third-party services
- **Zero analytics/advertising trackers, cookies, or third-party `<script>` tags found anywhere**
  in the frontend (`frontend/index.html`, `package.json`, and a full grep for GA/GTM/Meta
  Pixel/Mixpanel/Hotjar/Segment/Sentry/etc. all came back empty).
- SMS and push notifications are **dev-stub only** (`LoggingSmsProvider`/`LoggingPushProvider`) —
  no real SMS/push vendor is integrated yet.
- Email goes through SMTP, Mailtrap sandbox by default, swappable to real SMTP.
- Payments: **Razorpay** (mock provider by default in this environment).
- Maps: Leaflet/OpenStreetMap client-side only, no geocoding API call found.
- File storage: local disk only, despite the README's aspirational "S3" mention — no cloud storage
  integration actually exists in the code.
- No logging/observability SaaS — logs are local files only.

### 1.3 Existing legal pages
**None existed.** No Privacy Policy, Terms, or Cookie Policy page anywhere in the frontend before
this branch, and no footer component of any kind.

### 1.4 Security gaps flagged (see §5 below for the full list and current status)
CAPTCHA, encryption, HTTPS, actuator exposure, and token storage were all audited — see §5.

---

## 2. Decisions made

| Decision | Rationale |
|---|---|
| Consent + data-rights storage lives in **customer-service**, not a new service | It already owns `Customer`/`CustomerAddress`; matches the "no shared entities across services" convention already established in this codebase. `ConsentRecord.customerId`/`DataRightsRequest.customerId` are plain UUIDs, not JPA relationships — same pattern as every other cross-boundary reference in this codebase. |
| Consent is **upserted per (customer, purpose)**, never deleted | DPDP requires being able to *prove* consent was given/withdrawn. Deleting the record would destroy that evidence. History of changes is captured via the platform's existing generic `AuditLogService` (`@Audited` on the service methods), not a bespoke append-only log. |
| `ESSENTIAL_SERVICE` is shown as a checkbox but required to proceed | DPDP doesn't require "consent" for processing necessary to deliver the contracted service — but the checkbox still records an explicit acknowledgment of the Privacy Notice at signup, which is useful evidence regardless of legal basis. The other three purposes (`MARKETING_COMMUNICATIONS`, `LOCATION_TRACKING`, `ANALYTICS_COOKIES`) are genuine, unticked-by-default opt-ins. |
| Data-rights submission (`POST /data-rights-requests/submit`) is **public, no login required** | A data principal without a Farm2Home account (e.g. objecting to being contacted) must still be able to reach the grievance channel. Discovered mid-implementation that this required **three separate changes**, not one: customer-service's own `SecurityConfig`, the **API Gateway's own, separate `JwtAuthenticationFilter`** (which has its own hardcoded public-path allowlist and would have 401'd the request before it ever reached customer-service), and the gateway's route predicate list (the path wasn't under the existing `/api/v1/customers/**` prefix at all). Used a distinct `/submit` sub-path specifically so the public exemption doesn't accidentally also expose the admin list/triage endpoints, since the gateway's filter matches by `startsWith` with no per-HTTP-method distinction. |
| Admin-side data-rights triage (`GET`/`PATCH`) is **SUPER_ADMIN only** | No dedicated "compliance officer" role exists in this system, and inventing one wasn't asked for. Flagged as an open item (§6) — a real deployment likely wants a narrower role than "every SUPER_ADMIN." |
| Tracker-gating banner built even though **zero trackers currently exist** | The task explicitly asked for non-essential trackers to be gated behind consent. Since there's nothing to gate today, `frontend/src/utils/trackerConsent.ts` is forward-looking scaffolding: a `loadTrackerScript()` gate that's a no-op until `ANALYTICS_COOKIES` consent is granted, so the *first* tracker anyone adds has somewhere safe to plug into rather than being wired in ungated. |
| Legal/Terms/Privacy pages are **standalone routes outside `MainLayout`/`ProtectedRoute`** | Must be reachable whether or not the visitor is logged in — same rationale as `LoginPage`/`RegisterPage` already sitting outside `MainLayout`. |
| Branch created from current working tree (not a clean checkout from `main`) | `git checkout -b` is non-destructive and carries uncommitted changes forward; there were substantial pre-existing uncommitted changes on `main` from other in-progress work, and creating the branch does not discard or commit any of it. |

---

## 3. What was built

### Backend (`customer-service`)
- `domain/enums/ConsentPurpose.java` — `ESSENTIAL_SERVICE`, `MARKETING_COMMUNICATIONS`,
  `LOCATION_TRACKING`, `ANALYTICS_COOKIES`.
- `domain/entity/ConsentRecord.java` + Flyway `V7__create_dpdp_consent_and_rights_requests.sql` —
  one row per customer+purpose, unique constraint, never soft-deleted.
- `ConsentRepository`, `ConsentServiceImpl`, `ConsentController` —
  `POST /api/v1/customers/me/consents` (bulk upsert), `GET /api/v1/customers/me/consents`,
  `PUT /api/v1/customers/me/consents/{purpose}` (single-purpose grant/withdraw). Self-service only,
  customerId always from the bearer token, matching `CustomerController`'s `/me/addresses`
  convention exactly. Records IP address and User-Agent alongside each consent choice.
- `domain/enums/DataRightsRequestType.java` (`ACCESS`/`CORRECTION`/`ERASURE`/`WITHDRAW_CONSENT`/
  `GRIEVANCE`/`OTHER`), `DataRightsRequestStatus.java` (`NEW`/`IN_PROGRESS`/`RESOLVED`/`REJECTED`).
- `domain/entity/DataRightsRequest.java` (same migration) — `customerId` nullable (anonymous
  submissions), `requesterName`/`requesterContact` captured directly, never soft-deleted (it's the
  compliance evidence of what was requested and how it was handled).
- `DataRightsRequestRepository`, `DataRightsRequestServiceImpl`, `DataRightsRequestController` —
  `POST /api/v1/data-rights-requests/submit` (public), `GET /api/v1/data-rights-requests`
  (SUPER_ADMIN, paginated list), `PATCH /api/v1/data-rights-requests/{id}/status` (SUPER_ADMIN,
  triage).
- `config/SecurityConfig.java` — added the narrow public-POST exemption for `/submit`.
- Tests: `ConsentControllerTest` (3 tests), `DataRightsRequestControllerTest` (4 tests) — all
  passing, plus the full existing 112-test customer-service suite re-run with **zero regressions**.

### Backend (`api-gateway`)
- `filter/JwtAuthenticationFilter.java` — added `/api/v1/data-rights-requests/submit` to
  `PUBLIC_PATHS`.
- `application.yml` — added `/api/v1/data-rights-requests/**` to the customer-service route's path
  predicates (same route/resilience config as the existing `/api/v1/customers/**` block).

### Frontend
- `types/consent.types.ts`, `types/dataRights.types.ts`, `services/consentService.ts`,
  `services/dataRightsService.ts` — `consentService.record()` retries through the same
  async-Kafka-customer-creation race as `customerService.addAddress()` (up to 4 attempts, 500ms
  backoff on 404).
- `constants/legal.ts` — single source of truth for the Grievance Officer contact and company
  name, referenced from the footer, Privacy Notice, and Terms so they can't drift out of sync.
  **Every value in this file is a `[LEGAL REVIEW]` placeholder.**
- `components/legal/LegalPageLayout.tsx`, `LegalReviewNote.tsx` — shared shell + a visible
  warning-banner component used to flag every block of unreviewed legal copy in place, not just in
  this progress file.
- `pages/legal/PrivacyPolicyPage.tsx` (route `/privacy`) — what data, why, retention (honestly
  states retention is **not yet defined** — see §5), third parties, DPDP rights, Grievance Officer
  contact, Data Protection Board mention.
- `pages/legal/TermsPage.tsx` (route `/terms`) — minimal skeleton (no Terms page existed before
  this) plus the data-protection clause specifically requested, cross-referencing the Privacy
  Notice and the data-rights form.
- `pages/legal/DataRightsRequestPage.tsx` (route `/data-rights-request`) — public form: name,
  contact, request type (access/correction/erasure/withdraw-consent/grievance/other), free-text
  details.
- `components/consent/ConsentBanner.tsx` + `utils/trackerConsent.ts` — global banner (mounted
  once in `App.tsx`), gates the `ANALYTICS_COOKIES` purpose; `loadTrackerScript()` is the gate any
  future tracker should be loaded through.
- `pages/auth/RegisterPage.tsx` — added four **unticked-by-default** consent checkboxes to Step 1
  (Personal Details), right where the account is actually created. `ESSENTIAL_SERVICE` blocks
  proceeding if unchecked (inline error, not a silent disable); the other three are optional.
  Consent is submitted best-effort right after `authService.register()` succeeds, same
  non-blocking tolerance the existing address step already uses for the same race condition.
- `components/layout/PublicFooter.tsx` — grievance contact + Privacy/Terms/Data-Rights links.
  Added to `MainLayout.tsx` (every authenticated page) and, in compact form, to `LoginPage.tsx`
  and `RegisterPage.tsx` (which sit outside `MainLayout` and needed their own insertion point).
- `routes/AppRoutes.tsx` — `/privacy`, `/terms`, `/data-rights-request` added as public routes
  outside `ProtectedRoute`/`MainLayout`.
- Verified: `tsc --noEmit` clean, `npm run build` succeeds.

### Root documents
- `BREACH_RUNBOOK.md` — severity classification, roles, detection/containment steps, 72-hour Board
  notification checklist + template, user notification template, post-incident review, and an
  explicit tie-back to the security gaps in §5 (the actuator exposure in particular is called out
  as a standing risk factor that makes the exact scenario this runbook exists for more likely).
- `DPDP_PROGRESS.md` — this file.

---

## 4. Needs lawyer / business review before this ships to real users

Every `[LEGAL REVIEW]` marker in the code points back to one of these:

1. **Grievance Officer identity** (`frontend/src/constants/legal.ts`) — name, email, phone,
   postal address are all placeholders. A real person/team must be appointed and staffed before
   the Privacy Notice's contact info is real.
2. **Company legal name** — placeholder pending confirmation of the correct registered entity name.
3. **Privacy Notice content** (`PrivacyPolicyPage.tsx`) — drafted directly from the engineering
   data audit (§1.1), not from a product/legal spec. Needs confirmation it's complete and
   accurate, plus legal sign-off on the exact wording, before publishing.
4. **Terms & Conditions is a skeleton, not a complete agreement** (`TermsPage.tsx`) — only the
   data-protection clause was authored as part of this DPDP pass. Standard commercial sections
   (eligibility, pricing/payment terms, cancellation/refunds, delivery obligations, liability,
   governing law, termination) are **not drafted** and need separate authorship.
5. **Retention periods are not defined anywhere** — the Privacy Notice section on retention says
   this honestly rather than promising a period the system can't back up (see §5.4). Legal/business
   needs to define actual retention periods per data category, which engineering then needs to
   implement.
6. **BREACH_RUNBOOK.md's 72-hour target and notification thresholds** — based on common practice,
   not a confirmed reading of the (at time of writing, still-being-finalized) DPDP Rules. Needs
   confirmation against the Rules once notified.
7. **Consent copy wording** (`CONSENT_PURPOSE_LABELS` in `types/consent.types.ts`) — must match
   whatever the Privacy Notice's final wording says once legal has reviewed it; the two currently
   describe the same purposes independently and could drift.

---

## 5. Security gaps flagged (audited, **not fixed** — out of scope for this compliance pass unless noted)

The task asked specifically to *flag* these, not silently fix them (a security fix of this size
deserves its own reviewed change, not to be bundled invisibly into a compliance-docs branch). Each
one is also referenced from `BREACH_RUNBOOK.md` §8 as a standing incident-risk factor.

1. **No CAPTCHA/bot-protection anywhere.** Confirmed via a repo-wide search for
   captcha/recaptcha/turnstile/hcaptcha — zero matches. Registration, login, and OTP-resend are
   all unprotected against automated abuse.
2. **No field-level encryption of personal data at rest.** A repo-wide search for
   encrypt/AES/Cipher/GCM found **zero matches** in the backend. Mobile numbers, addresses, and
   GPS coordinates are plain, unencrypted columns. (The task asked specifically to check for
   "fail-open encryption" — the actual finding is more fundamental: there's no encryption
   implementation to fail open *or* closed. The only cryptographic code in the backend,
   `RazorpaySignature.isValid()`, is HMAC signature verification for payment webhooks, and it is
   correctly fail-closed — any exception returns `false` rather than accepting the payload.)
3. **No HTTPS/TLS configured anywhere.** No `server.ssl` block, no HSTS header, no CSP, in any
   service's `application.yml`, and `frontend/nginx.conf` listens on plain port 80 with no
   redirect. This is clearly dev-only configuration as it stands (matches the Mailtrap-sandbox/
   mock-payment-gateway/logging-SMS defaults throughout), but there is **no evidence in the repo of
   a production TLS story** — the README's "AWS EC2/RDS/S3/ELB" deployment plan has no actual
   Terraform/k8s manifests to inspect for a TLS-terminating load balancer.
4. **Every service's `/actuator/env` is unauthenticated** (`management.endpoints.web.exposure.
   include: health,info,metrics,prometheus,env` combined with `/actuator/**` being `permitAll()`'d
   in every service's `SecurityConfig`) — this dumps every resolved Spring property, including
   `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`,
   and `MAIL_PASSWORD`, to anyone who can reach the port. **This is the single highest-priority
   finding from this entire audit** — if `docker-compose.yml`'s direct host-port-publishing
   pattern is replicated in production, this is a directly exploitable path to a full
   credential-leak breach. Recommend prioritizing this above the DPDP paperwork itself.
5. **JWTs live in `localStorage`/`sessionStorage`, not an httpOnly cookie** — any XSS anywhere in
   the frontend can read tokens directly via JS.
6. **No erasure/anonymization capability exists.** Every `delete()` across the codebase is a soft
   delete (`is_deleted = true`) — a "deleted" customer's name, mobile, email, address, and GPS
   coordinates remain in the database indefinitely. This directly blocks the Right to Erasure that
   `DataRightsRequestType.ERASURE` now lets a user *request* — the request can be logged and
   triaged, but **there is currently no technical mechanism to actually fulfil it**. Flagged
   explicitly in the Privacy Notice's retention section rather than glossed over.
7. **No retention/purge job for personal data.** The only scheduled deletion job in the entire
   backend is OTP expiry cleanup (`auth-service` `OtpService.purgeExpiredOtps()`, every 10
   minutes). Customer profiles, addresses, delivery GPS trails, payment records, and the
   platform-wide audit log all accumulate indefinitely with no automatic expiry.

---

## 6. Open items (not built, in scope for a future pass)

- **Erasure/anonymization implementation** — the actual mechanism to fulfil an `ERASURE` request
  (§5.6). This is the biggest functional gap between "we now have a form to request erasure" and
  "we can actually honour that request."
- **Defined, enforced retention periods** per data category, plus a scheduled purge/anonymization
  job — currently only OTPs expire automatically.
- **Consent-withdrawal UI on an authenticated settings screen** — the backend API
  (`PUT /me/consents/{purpose}`) and service method exist, but there's no frontend settings page
  wired to it yet; today a logged-in user can only set consent at registration.
- **Admin UI for the data-rights-request queue** — the backend list/triage API
  (`GET`/`PATCH /data-rights-requests`) exists and is tested, but there's no frontend admin screen
  built to consume it yet; an admin would need to call the API directly today.
- **A narrower "compliance officer" role** instead of gating data-rights admin access behind
  `SUPER_ADMIN` — not built since no such role exists in this system yet and inventing one wasn't
  asked for.
- **Rate-limiting/CAPTCHA specifically on the new public `POST /data-rights-requests/submit`
  endpoint** — it's a new unauthenticated write endpoint on a system that already has no CAPTCHA
  anywhere (§5.1); it inherits that existing gap rather than introducing a new one, but is worth
  calling out since it's newly public as of this branch.
- **Cookie Policy page** — not built, since the audit found zero cookies are actually set by this
  app today. If real cookies are ever introduced (vs. the current localStorage/sessionStorage-only
  approach), this becomes a real gap.
- Everything in §4 (lawyer/business review) is also, by definition, an open item until reviewed.
