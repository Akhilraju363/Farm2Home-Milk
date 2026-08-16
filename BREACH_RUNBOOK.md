# Farm2Home — Personal Data Breach Runbook

**Status: DRAFT.** This runbook was drafted by engineering as part of a DPDP Act 2023 compliance
pass (see `DPDP_PROGRESS.md`). The notification timeline, thresholds, and templates below are
based on the DPDP Act's breach-notification obligation (s.8(6)) and common practice for
similar regimes — **the exact prescribed form, manner, and timeline must be confirmed against the
final Digital Personal Data Protection Rules once notified, by legal counsel**, before this is
treated as the company's official incident-response policy. Every `[LEGAL REVIEW]` marker below is
a specific open question.

---

## 1. What counts as a "personal data breach"

Under the DPDP Act, a personal data breach is any unauthorised processing of personal data, or
accidental disclosure, acquisition, sharing, use, alteration, destruction, or loss of access to
personal data, that compromises its confidentiality, integrity, or availability.

For Farm2Home specifically, this includes (non-exhaustive — see `DPDP_PROGRESS.md` for the full
data inventory):

- Unauthorised access to the `auth`/`customer`/`payment`/`delivery` service databases or backups.
- A leaked/stolen credential (JWT secret, database password, Razorpay API secret, SMTP password)
  that could be used to access personal data — **including via the unauthenticated
  `/actuator/env` exposure flagged in `DPDP_PROGRESS.md`, which is a currently-open door to
  exactly this class of incident.**
- Loss or theft of a device/backup containing personal data.
- A misconfigured access control (e.g. an admin endpoint reachable without authentication) that
  exposed customer PII, delivery-partner GPS history, or payment references.
- A third-party processor (Razorpay, email provider) itself suffering a breach affecting
  Farm2Home data.
- Accidental exposure via logs, error messages, or a public repository/paste containing personal
  data or secrets.

## 2. Severity classification

| Severity | Definition | Examples |
|---|---|---|
| **Critical** | Large-scale or sensitive-category exposure; ongoing unauthorised access; credentials/secrets exposed that grant broad access. | Database dump exfiltrated; `JWT_SECRET` or DB credentials leaked publicly; mass customer PII exposure. |
| **High** | Confirmed unauthorised access/disclosure affecting a bounded but meaningful set of data principals. | A single compromised admin account used to export a customer list; a misconfigured endpoint exposing addresses/GPS for an unknown but non-trivial number of customers. |
| **Medium** | Limited/contained exposure, quickly remediated, low likelihood of misuse. | A single customer's data briefly visible to another logged-in customer due to a caching bug, caught and fixed same day. |
| **Low** | Near-miss or internal-only exposure with no external party involved. | An engineer's local `.env` file with dev-only mock credentials committed then immediately reverted; no real customer data touched. |

**Critical/High severity always triggers the Board-notification and (if the threshold in §5 is
met) user-notification process below. Medium/Low still get logged and reviewed (§7), but may not
require external notification** — `[LEGAL REVIEW]` confirm the exact threshold for mandatory Board
notification; the DPDP Act does not appear to carve out a materiality floor the way some other
regimes do, so the safe default until confirmed is: **any confirmed breach affecting real user
data gets reported to the Board**, regardless of scale.

## 3. Roles and responsibilities

| Role | Responsibility | Who (fill in) |
|---|---|---|
| Incident Commander | Owns the incident end-to-end: coordinates containment, decides severity, approves notifications. | `[LEGAL REVIEW / BUSINESS: name a person/role]` |
| Engineering Lead | Technical containment, root-cause investigation, evidence preservation. | `[fill in]` |
| Grievance Officer | Point of contact for affected data principals; drafts/sends user notifications. | See `frontend/src/constants/legal.ts` `GRIEVANCE_OFFICER` (placeholder — must be a real, staffed contact). |
| Legal Counsel | Confirms notification obligations/timeline/wording, liaises with the Data Protection Board. | `[fill in]` |
| Board notifier | Actually files the notification to the Data Protection Board of India. | `[fill in — likely Legal Counsel or Incident Commander]` |

## 4. Detection, triage, and containment (first 24 hours)

1. **Detect.** Via monitoring alert, user report (including via the Grievance Officer channel or
   the `/data-rights-request` form — a GRIEVANCE-type submission mentioning unauthorized access
   should be triaged as a potential breach report, not just a routine complaint), third-party
   notification (e.g. Razorpay), or internal discovery.
2. **Log it immediately** — timestamp, who found it, what's known so far. This becomes the
   incident record.
3. **Assemble the response team** (§3 roles) and assign an Incident Commander.
4. **Contain.** Depending on the vector:
   - Compromised credential/secret → rotate it immediately (DB password, JWT signing secret,
     Razorpay keys, SMTP password) and invalidate active sessions if JWT-related.
   - Exposed endpoint → restrict/disable it (e.g. lock down `/actuator/**` exposure — see §8).
   - Compromised account → force password reset, revoke sessions.
   - Ongoing exfiltration → block the source (IP/network ACL) if identifiable.
5. **Preserve evidence** before remediating where possible — logs, database snapshots, the request
   that triggered the exposure — needed both for root-cause analysis and for accurately describing
   the breach in the Board/user notifications.
6. **Assess scope**: which data categories, how many data principals, what data fiduciary/processor
   systems were involved, whether the breach is ongoing or contained.
7. **Classify severity** (§2) based on what's known — re-classify as more information emerges.

## 5. Data Protection Board notification (target: within 72 hours of *becoming aware*)

`[LEGAL REVIEW]` The DPDP Act requires the Data Fiduciary to notify the Data Protection Board of
India of a personal data breach "in such form and manner as may be prescribed." **72 hours from
awareness is used here as the internal working target** (consistent with equivalent regimes and
good practice), pending confirmation of the Board's actual prescribed form/manner/timeline once
published. Do not wait for full certainty about scope before making the initial notification —
notify with what's known and follow up as the investigation progresses, same practice as other
breach-notification regimes.

### 5.1 Notification checklist
- [ ] Incident Commander assigned and severity classified (§2).
- [ ] Nature of the breach: what happened, how it was discovered, when it occurred vs. when discovered.
- [ ] Categories and approximate number of data principals affected.
- [ ] Categories of personal data involved (see `DPDP_PROGRESS.md` data inventory — e.g. names,
      mobile numbers, addresses/GPS coordinates, payment references, delivery-partner location data).
- [ ] Likely consequences of the breach.
- [ ] Containment/remediation measures already taken and planned.
- [ ] Contact point for the Board to request more information (Grievance Officer / Legal Counsel).

### 5.2 Board notification template (DRAFT)

```
To: Data Protection Board of India
From: [Company Legal Name — see COMPANY_LEGAL_NAME placeholder]
Subject: Personal Data Breach Notification — [Incident ID/date]

1. Nature of the breach:
   [What happened — unauthorised access / disclosure / loss, how discovered, when it
   occurred and when it was discovered.]

2. Categories and approximate number of data principals affected:
   [e.g. "Approximately N Farm2Home customers" / "N delivery partners"]

3. Categories of personal data involved:
   [e.g. name, mobile number, delivery address including GPS coordinates, order history.
   Explicitly state whether payment card/UPI/bank details were involved — per our current
   architecture they should not be, since we do not store them (see DPDP_PROGRESS.md), but
   confirm this explicitly for this incident.]

4. Likely consequences:
   [e.g. risk of unsolicited contact, physical safety risk given address/GPS data, financial
   fraud risk if payment references were exposed.]

5. Measures taken/proposed:
   [Containment actions taken, planned remediation, whether affected individuals have been
   or will be notified directly.]

6. Contact point:
   [Grievance Officer name/email/phone — see frontend/src/constants/legal.ts]
```

## 6. User notification

`[LEGAL REVIEW]` Confirm the exact threshold/criteria under the DPDP Rules for when individual
notification to affected data principals is mandatory vs. discretionary. Until confirmed, the
default here is: **notify affected users whenever the breach is High or Critical severity, or
whenever the Board notification (§5) is filed** — being conservative in favour of notifying is
safer than under-notifying.

### 6.1 User notification template (DRAFT)

```
Subject: Important: A security incident affecting your Farm2Home account

Dear [First Name],

We're writing to let you know about a security incident that may have affected your personal
data on Farm2Home.

What happened:
[Plain-language description — avoid jargon. E.g. "Between [date] and [date], [what happened]."]

What data was involved:
[Specific to this user if possible — e.g. "your name, mobile number, and delivery address."
Do not use vague boilerplate if more specific detail is available.]

What we're doing about it:
[Containment/remediation steps already taken, and what's still in progress.]

What you can do:
[Practical guidance — e.g. "we recommend changing your password" if credentials may be
affected; "please be alert to unexpected calls/messages referencing your address" if
address/location data was involved.]

Questions or concerns:
Contact our Grievance Officer at [GRIEVANCE_OFFICER.email] / [GRIEVANCE_OFFICER.phone].
You may also raise this through our Data Rights Request form: [link to /data-rights-request].
You may also approach the Data Protection Board of India if you are not satisfied with our
response.

We take the security of your data seriously and apologise for this incident.

Farm2Home
```

## 7. Post-incident review (within 2 weeks of resolution)

- Root cause: what actually allowed the breach, not just the immediate trigger.
- Timeline: detection → containment → notification, with actual timestamps, to check the 72-hour
  target was met (or document why not).
- What worked / what didn't in the response.
- Concrete remediation items with owners and dates, tracked to completion — not just "we'll fix it."
- Update this runbook if the process itself revealed a gap.

## 8. Known current risk factors (from the DPDP compliance audit)

These are standing gaps identified during the engineering audit in `DPDP_PROGRESS.md` that make
certain breach scenarios *more* likely today, and should be prioritized for remediation
independent of any actual incident:

1. **Every service's `/actuator/env` endpoint is exposed with no authentication**, and would leak
   `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`,
   and `MAIL_PASSWORD` to anyone who can reach the service's port. This is the single highest-risk
   item — if this environment's port-publishing pattern is replicated in production, it is a
   directly exploitable path to exactly the kind of credential-leak breach this runbook exists to
   respond to. **Recommend closing this before it causes an incident, not after.**
2. **No HTTPS/TLS enforced anywhere** in the current configuration (gateway, services, or nginx)
   — personal data in transit is not protected from network-level interception in the deployment
   configuration as it stands.
3. **No CAPTCHA/bot-protection** on login or registration — increases risk of credential-stuffing
   attacks against customer accounts.
4. **No field-level encryption of personal data at rest** — mobile numbers, addresses, and GPS
   coordinates are stored as plain columns, so a database-level compromise exposes them directly
   with no additional layer to defeat.
5. **JWTs are stored in `localStorage`/`sessionStorage`**, not an httpOnly cookie — an XSS
   vulnerability anywhere in the frontend would allow direct token theft.

See `DPDP_PROGRESS.md` → "Security gaps flagged" for full detail and file references on each.
