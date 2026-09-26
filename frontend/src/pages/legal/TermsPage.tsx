import { Typography, Box, List, ListItem, ListItemText } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { LegalPageLayout } from '../../components/legal/LegalPageLayout'
import { LegalReviewNote } from '../../components/legal/LegalReviewNote'
import { COMPANY_LEGAL_NAME, GRIEVANCE_OFFICER } from '../../constants/legal'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" fontWeight={700} gutterBottom>{title}</Typography>
      {children}
    </Box>
  )
}

/** DRAFT - no Terms & Conditions page existed in this app before the 2026-09 DPDP compliance
 *  pass (confirmed by codebase audit - see DPDP_PROGRESS.md). Expanded during the 2026-09-25
 *  legal/accessibility audit with the standard commercial sections that pass deliberately left
 *  unwritten (see its own [LEGAL REVIEW] note below at the time). Sections describing how the
 *  service actually behaves (orders, subscriptions, payments, delivery) are written directly from
 *  the order/payment/subscription/delivery service code, not invented. Sections that are pure
 *  legal-judgment calls (liability, governing law, dispute resolution) are left as explicit
 *  [LEGAL REVIEW] placeholders rather than filled with generic template language - this is not a
 *  complete, counsel-approved commercial agreement. */
export function TermsPage() {
  return (
    <LegalPageLayout title="Terms & Conditions" lastUpdated="DRAFT - not yet published">
      <LegalReviewNote>
        This entire page is an engineering-drafted DRAFT pending legal review. It has not been
        approved by counsel or business ownership and must not be treated as Farm2Home's binding
        Terms & Conditions until reviewed. Every [LEGAL REVIEW] marker below is a specific open
        question, not filler.
      </LegalReviewNote>

      <Section title="1. Acceptance of terms">
        <Typography variant="body2">
          By creating a Farm2Home account or placing an order, you agree to these Terms &
          Conditions, our{' '}
          <Typography component={RouterLink} to="/privacy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Privacy Notice
          </Typography>, and our{' '}
          <Typography component={RouterLink} to="/refund-policy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Refund & Cancellation Policy
          </Typography>.
        </Typography>
      </Section>

      <Section title="2. Eligibility and your account">
        <Typography variant="body2" paragraph>
          You need a Farm2Home account to place an order. Registration requires a valid 10-digit
          Indian mobile number, verified by a one-time password sent to that number. You are
          responsible for keeping your password confidential and for all activity that happens
          under your account; tell us immediately if you believe your account has been accessed
          without your permission.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW] This application does not currently collect date of birth, so it cannot
          technically verify a user's age. Decide the minimum-age requirement for using Farm2Home
          and how (if at all) it should be stated or enforced.
        </LegalReviewNote>
      </Section>

      <Section title="3. Orders and subscriptions">
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText primary="One-time orders" secondary="You choose products, quantities, and a delivery address at checkout." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Subscriptions" secondary="You can set up a recurring milk subscription with a chosen milk type, quantity, and delivery slot. You can pause a subscription until a date you choose, resume it at any time, or cancel it - see our Refund & Cancellation Policy for what cancelling does and does not affect financially." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Order acceptance" secondary="We may be unable to fulfil an order or a subscription delivery due to product availability or delivery-area limits; if that happens for something you already paid for, see our Refund & Cancellation Policy." />
          </ListItem>
        </List>
      </Section>

      <Section title="4. Pricing and payment">
        <Typography variant="body2" paragraph>
          Prices shown at checkout are what you pay for that order. You can pay by UPI/online
          payment, your Farm2Home wallet balance, or cash on delivery, depending on what's offered
          for your order. Online payments are processed by our payment gateway (Razorpay) - see
          our{' '}
          <Typography component={RouterLink} to="/privacy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Privacy Notice
          </Typography>{' '}
          for what we do and don't see of that transaction.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW / BUSINESS DECISION REQUIRED] Pricing policy (how/when prices can change,
          taxes/fees breakdown, currency) has not been defined in a business document available for
          this audit. Do not add specific pricing commitments here until that's decided.
        </LegalReviewNote>
      </Section>

      <Section title="5. Delivery">
        <Typography variant="body2" paragraph>
          You choose a delivery slot and address when ordering or subscribing. We aim to deliver
          within your chosen slot, but delivery times can be affected by factors outside our
          control (traffic, weather, product availability). For an active delivery, you can track
          your delivery partner's live location in the app.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW / BUSINESS DECISION REQUIRED] No delivery-guarantee, missed-delivery, or
          delivery-area-coverage policy has been defined by the business. Do not word this section
          as a guaranteed delivery time until that is decided - see the general claims-audit
          guidance not to promise what cannot be substantiated.
        </LegalReviewNote>
      </Section>

      <Section title="6. Cancellations and refunds">
        <Typography variant="body2">
          See our{' '}
          <Typography component={RouterLink} to="/refund-policy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Refund & Cancellation Policy
          </Typography>{' '}
          for how order and subscription cancellation works, and how refunds are handled for each
          payment method.
        </Typography>
      </Section>

      <Section title="7. Ratings and reviews">
        <Typography variant="body2">
          If you leave a product rating or review, it must reflect your own genuine experience. We
          may remove a review that is fake, abusive, or does not relate to the product. Reviews
          reflect the opinions of the customers who wrote them, not statements made by Farm2Home.
        </Typography>
      </Section>

      <Section title="8. Acceptable use">
        <Typography variant="body2">
          You agree not to use Farm2Home to submit false information, interfere with the
          service's normal operation, or attempt to access another user's account or data without
          authorization.
        </Typography>
      </Section>

      <Section title="9. Intellectual property">
        <Typography variant="body2">
          The Farm2Home app, its design, and its content (excluding content you submit, such as
          reviews or your profile photo) belong to {COMPANY_LEGAL_NAME} or its licensors. You may
          use the app for your own personal ordering and account management; you may not copy,
          resell, or reverse-engineer it.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW] Confirm ownership/licensing status of images and other media used in the
          app before this statement is published - see the image-copyright findings in the
          2026-09-25 audit report.
        </LegalReviewNote>
      </Section>

      <Section title="10. Service availability">
        <Typography variant="body2">
          We work to keep Farm2Home available, but the app may be temporarily unavailable for
          maintenance or due to circumstances outside our control. We do not guarantee
          uninterrupted access.
        </Typography>
      </Section>

      <Section title="11. Limitation of liability">
        <LegalReviewNote>
          [LEGAL REVIEW] Liability limitation language has deliberate legal consequences and
          varies by jurisdiction and consumer-protection law - it has not been drafted here to
          avoid publishing an unreviewed clause that could be unenforceable or, worse, unfairly
          limit a genuine consumer right. Counsel must draft this section.
        </LegalReviewNote>
      </Section>

      <Section title="12. Account suspension or termination">
        <Typography variant="body2" paragraph>
          You can stop using Farm2Home at any time. We may suspend or deactivate an account that
          we reasonably believe is being used fraudulently or in violation of these Terms.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW] Confirm what account "deletion" should mean once an actual
          erasure/anonymization mechanism exists (see DPDP_PROGRESS.md "Open items" - today,
          deactivating an account does not delete the underlying data).
        </LegalReviewNote>
      </Section>

      <Section title="13. Governing law and disputes">
        <LegalReviewNote>
          [LEGAL REVIEW] Governing law, jurisdiction, and dispute-resolution mechanism (courts vs.
          arbitration) must be decided by counsel/business and are intentionally not filled in
          here with a generic default.
        </LegalReviewNote>
      </Section>

      <Section title="14. Changes to these terms">
        <Typography variant="body2">
          We may update these Terms from time to time. If we make a material change, we will
          update the "Last updated" date on this page. Continuing to use Farm2Home after a change
          takes effect means you accept the updated Terms.
        </Typography>
      </Section>

      <Section title="15. Data protection">
        <Typography variant="body2" paragraph>
          {COMPANY_LEGAL_NAME} processes your personal data in accordance with the Digital
          Personal Data Protection Act, 2023 and our{' '}
          <Typography component={RouterLink} to="/privacy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Privacy Notice
          </Typography>, which describes in full what data we collect, why, who we share it with,
          how long we keep it, and the rights available to you (access, correction, erasure, and
          withdrawal of consent).
        </Typography>
        <Typography variant="body2" paragraph>
          Where we rely on your consent for a particular purpose (for example, marketing
          communications), that consent is collected separately and specifically for that purpose,
          and you may withdraw it at any time through your account settings or by submitting a{' '}
          <Typography component={RouterLink} to="/data-rights-request" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Data Rights Request
          </Typography>. Withdrawing consent does not affect the lawfulness of processing carried
          out before the withdrawal, and does not affect processing that is necessary to provide
          the service itself (such as fulfilling an order already placed).
        </Typography>
        <Typography variant="body2">
          If your personal data is affected by a data breach as defined under the DPDP Act, we
          will notify you and the Data Protection Board of India in accordance with the Act and
          our internal breach-response process.
        </Typography>
      </Section>

      <Section title="16. Contact">
        <Typography variant="body2">{GRIEVANCE_OFFICER.name}, {GRIEVANCE_OFFICER.designation}</Typography>
        <Typography variant="body2">Email: {GRIEVANCE_OFFICER.email}</Typography>
        <Typography variant="body2">Phone: {GRIEVANCE_OFFICER.phone}</Typography>
      </Section>
    </LegalPageLayout>
  )
}
