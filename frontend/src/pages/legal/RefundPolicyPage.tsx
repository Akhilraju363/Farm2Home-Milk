import { Typography, List, ListItem, ListItemText, Box } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { LegalPageLayout } from '../../components/legal/LegalPageLayout'
import { LegalReviewNote } from '../../components/legal/LegalReviewNote'
import { GRIEVANCE_OFFICER } from '../../constants/legal'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" fontWeight={700} gutterBottom>{title}</Typography>
      {children}
    </Box>
  )
}

/** DRAFT - see DPDP_PROGRESS.md and the 2026-09-25 legal/accessibility audit. Written directly
 *  from the order/payment/subscription cancellation code (OrderServiceImpl.cancel,
 *  PaymentServiceImpl.refund, SubscriptionServiceImpl.cancel), not from a business policy
 *  document - because no refund-timeline/business policy document exists in this repo. Every
 *  timeframe/eligibility rule not actually enforced by the code is marked [LEGAL REVIEW /
 *  BUSINESS DECISION REQUIRED] rather than invented. */
export function RefundPolicyPage() {
  return (
    <LegalPageLayout title="Refund & Cancellation Policy" lastUpdated="DRAFT - not yet published">
      <LegalReviewNote>
        This entire page is an engineering-drafted DRAFT pending legal and business review. It
        describes only what the application actually does today; it does not promise a refund
        timeline, because none has been decided yet - see the [LEGAL REVIEW / BUSINESS DECISION
        REQUIRED] notes below. It has not been approved by counsel or business ownership.
      </LegalReviewNote>

      <Section title="1. Order cancellation">
        <Typography variant="body2" paragraph>
          You can cancel an order yourself at any point before it is marked Delivered. Once an
          order is Delivered, it can no longer be cancelled through the app.
        </Typography>
        <LegalReviewNote>
          [ENGINEERING NOTE] Confirmed from OrderServiceImpl.cancel: the app does not currently
          restrict cancellation by how close the order is to its delivery slot - an order can be
          cancelled even after it has been dispatched for delivery, right up until it is marked
          Delivered. [LEGAL REVIEW / BUSINESS DECISION REQUIRED] Decide whether a cut-off (e.g. no
          cancellation once "Out for Delivery") is wanted, since perishable milk already out for
          delivery may not be recoverable - if so, this needs a corresponding code change, not
          just a policy statement.
        </LegalReviewNote>
      </Section>

      <Section title="2. Refunds for a cancelled or unavailable order">
        <Typography variant="body2" paragraph>
          Cancelling an order does not automatically refund a payment already made for it. If you
          already paid for an order that is then cancelled (by you, or because a product became
          unavailable), our team issues the refund separately after reviewing the order.
        </Typography>
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText
              primary="Paid by wallet"
              secondary="Refunded as a credit back to your Farm2Home wallet."
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Paid by UPI or online payment (Razorpay)"
              secondary="Refunded through the payment gateway back to the original payment source (bank/UPI/card) - never credited to your in-app wallet instead."
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Paid by cash on delivery"
              secondary="There is no electronic payment to reverse. The refund is recorded in our system, and any physical cash return is handled directly with you outside the app."
            />
          </ListItem>
        </List>
        <LegalReviewNote>
          [LEGAL REVIEW / BUSINESS DECISION REQUIRED] No refund timeframe (e.g. "5-7 business
          days") is defined anywhere in this codebase or in any business document available for
          this audit. Do not publish a specific number of days until the business decides one -
          publishing an invented timeframe here would be a false commitment. Also confirm: (a) who
          is eligible to request a refund and under what circumstances (e.g. only for a cancelled
          or undelivered order, vs. also for a delivered-but-unsatisfactory product); (b) whether
          refunds for gateway-collected payments follow Razorpay's own refund-processing timeline
          once a real Razorpay account is connected (currently this environment only runs a mock
          payment gateway).
        </LegalReviewNote>
      </Section>

      <Section title="3. Damaged, spoiled, or incorrect delivery">
        <LegalReviewNote>
          [LEGAL REVIEW / BUSINESS DECISION REQUIRED] No process for reporting a damaged, spoiled,
          or incorrect delivery (e.g. wrong milk type/quantity) exists in the current application -
          there is no "report an issue with this order" flow. This is a real gap for a perishable
          dairy product and should be decided and built before this policy claims a resolution
          process. Until then, contact the Grievance Officer (Section 6) to raise an issue with a
          specific delivery.
        </LegalReviewNote>
      </Section>

      <Section title="4. Subscription cancellation">
        <Typography variant="body2" paragraph>
          You can cancel an active or paused subscription at any time from your account. Cancelling
          a subscription stops future deliveries under that subscription; it does not by itself
          refund any amount already paid for deliveries already made or already in progress.
        </Typography>
        <Typography variant="body2" paragraph>
          You can also pause a subscription until a date you choose, and resume it immediately
          whenever you like, without cancelling it.
        </Typography>
        <LegalReviewNote>
          [ENGINEERING NOTE] Confirmed from SubscriptionServiceImpl: cancelling a subscription is a
          status change only - there is no automatic proration or refund calculation for
          already-paid, not-yet-delivered subscription cycles. [LEGAL REVIEW / BUSINESS DECISION
          REQUIRED] Decide whether partial refunds for undelivered days in a paid subscription
          cycle should be offered, and if so, this needs new functionality, not just a policy
          statement.
        </LegalReviewNote>
      </Section>

      <Section title="5. Non-refundable situations">
        <LegalReviewNote>
          [LEGAL REVIEW / BUSINESS DECISION REQUIRED] No situations have been designated
          non-refundable by the business yet. Do not list any here until that decision is made -
          an invented exclusion could unfairly deny a legitimate refund.
        </LegalReviewNote>
      </Section>

      <Section title="6. How to request a refund or raise a delivery issue">
        <Typography variant="body2" paragraph>
          Cancel an order or subscription directly in the app. For anything else related to a
          refund, a damaged/incorrect delivery, or a payment dispute, contact our Grievance
          Officer:
        </Typography>
        <Typography variant="body2">{GRIEVANCE_OFFICER.name}, {GRIEVANCE_OFFICER.designation}</Typography>
        <Typography variant="body2">Email: {GRIEVANCE_OFFICER.email}</Typography>
        <Typography variant="body2" sx={{ mb: 1.5 }}>Phone: {GRIEVANCE_OFFICER.phone}</Typography>
        <Typography variant="body2">
          You can also submit a{' '}
          <Typography component={RouterLink} to="/data-rights-request" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Data Rights / Grievance request
          </Typography>{' '}
          through the app.
        </Typography>
      </Section>
    </LegalPageLayout>
  )
}
