import { Typography, List, ListItem, ListItemText, Box, Button } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { LegalPageLayout } from '../../components/legal/LegalPageLayout'
import { LegalReviewNote } from '../../components/legal/LegalReviewNote'
import { GRIEVANCE_OFFICER, COMPANY_LEGAL_NAME, DATA_PROTECTION_BOARD_NOTE } from '../../constants/legal'
import { isGoogleMapsConfigured } from '../../utils/googleMapsConfig'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" fontWeight={700} gutterBottom>{title}</Typography>
      {children}
    </Box>
  )
}

/** DRAFT - see DPDP_PROGRESS.md. This is a first-pass Privacy Notice built directly from an
 *  engineering audit of what the codebase actually collects/does (see the "Personal data
 *  collection points" section of that file) - it is not a substitute for legal review, and every
 *  [LEGAL REVIEW] marker below is a specific open question for counsel/business, not filler. */
export function PrivacyPolicyPage() {
  return (
    <LegalPageLayout title="Privacy Notice" lastUpdated="DRAFT - not yet published">
      <LegalReviewNote>
        This entire page is an engineering-drafted DRAFT pending legal review. It has not been
        approved by counsel and must not be treated as Farm2Home's binding privacy policy until
        reviewed and the version below is finalized. See DPDP_PROGRESS.md at the repo root for the
        full list of open questions.
      </LegalReviewNote>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        This notice explains what personal data {COMPANY_LEGAL_NAME} ("Farm2Home", "we") collects
        through the Farm2Home Milk app and website, why, who we share it with, how long we keep
        it, and the rights you have over it under the Digital Personal Data Protection Act, 2023
        ("DPDP Act").
      </Typography>

      <Section title="1. What personal data we collect">
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText primary="Account details" secondary="First name, last name, mobile number, email address (optional), and password (stored as a salted hash, never in plain text)." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Delivery address" secondary="House/flat number, street, area, city, district, state, PIN code, and the geographic coordinates (latitude/longitude) of that address." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Profile photo" secondary="If you choose to upload one." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Order and milk-preference details" secondary="What you order, delivery slot, and quantity/frequency preferences." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Payment references"
              secondary="We do not store your card number, UPI ID, or bank account details - these are handled directly by our payment gateway (Razorpay). We store only the order amount, status, and Razorpay's own transaction reference."
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Delivery-partner location"
              secondary="If you are a Farm2Home delivery partner, we record your GPS location during an active delivery so a customer can see real-time delivery tracking."
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Technical/activity data"
              secondary="IP address, browser/device information, and a log of actions taken on your account (for security and to investigate misuse)."
            />
          </ListItem>
        </List>
        <LegalReviewNote>
          [LEGAL REVIEW] Confirm this list is complete and matches every field actually collected
          across the app before publishing - it was compiled by reading the codebase's data model
          directly (Customer, CustomerAddress, DeliveryLocation, Payment entities), not from a
          product spec.
        </LegalReviewNote>
      </Section>

      <Section title="2. Why we process your data">
        <List dense disablePadding>
          <ListItem disableGutters><ListItemText primary="To create and manage your account." /></ListItem>
          <ListItem disableGutters><ListItemText primary="To fulfil and deliver your orders, including live delivery tracking." /></ListItem>
          <ListItem disableGutters><ListItemText primary="To process payments through our payment gateway." /></ListItem>
          <ListItem disableGutters><ListItemText primary="To respond to support requests and grievances." /></ListItem>
          <ListItem disableGutters><ListItemText primary="To detect and prevent fraud or misuse, and to meet legal/tax record-keeping obligations." /></ListItem>
          <ListItem disableGutters><ListItemText primary="With your separate, opt-in consent only: to send you marketing offers and product updates." /></ListItem>
        </List>
      </Section>

      <Section title="3. Who we share it with">
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText primary="Razorpay (payment gateway)" secondary="Receives what it needs to process a payment. We never see or store your card/UPI/bank details ourselves." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Our email delivery provider" secondary="Used to send account and order emails." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Our delivery partners" secondary="See your name, delivery address, and contact number needed to complete your delivery." />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Google Identity Services"
              secondary={'Loaded on every page so you can use "Sign in with Google." If you use it, the credential Google returns is sent to us to sign you in. Google\'s own script may set its own identifiers under google.com, outside our control.'}
            />
          </ListItem>
          {isGoogleMapsConfigured() && (
            <ListItem disableGutters>
              <ListItemText
                primary="Google Maps"
                secondary="Used to display live delivery-tracking maps. Loading the map exchanges data directly between your device and Google, per Google's own terms."
              />
            </ListItem>
          )}
        </List>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          See our{' '}
          <Typography component={RouterLink} to="/cookie-policy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Cookie Policy
          </Typography>{' '}
          for the full detail on what's stored in your browser and which third-party scripts run.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW / ENGINEERING NOTE] As of this audit, Farm2Home does not use any
          third-party analytics, advertising, or marketing-tracking service, and SMS/push
          notifications are not yet connected to a real provider (they currently only log locally
          in our systems). Google Identity Services (Sign-In) and, where configured, Google Maps
          are genuine third-party integrations already in use and are listed above - confirm this
          disclosure is sufficient. If the analytics/SMS/push situation changes before this notice
          is published, this section and the consent-banner copy must be updated to name the new
          provider(s).
        </LegalReviewNote>
      </Section>

      <Section title="4. How long we keep it">
        <Typography variant="body2" paragraph>
          We keep your account and order data for as long as your account is active, and for a
          further period afterwards as needed for tax, accounting, or dispute-resolution purposes.
          One-time verification codes (OTPs) are deleted automatically within minutes of expiring.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW - IMPORTANT] Engineering audit finding: Farm2Home does not currently have a
          defined retention period or an automated deletion/anonymization process for most
          personal data categories (customer profiles, addresses, delivery GPS history, payment
          records, activity logs). "Deleting" an account today only marks it inactive internally -
          it does not remove the underlying data. This section cannot honestly promise a specific
          retention period, and the Right to Erasure (Section 5 below) cannot be technically
          fulfilled yet, until engineering builds an actual deletion/anonymization process. Do not
          publish a specific retention promise until that exists - see DPDP_PROGRESS.md "Open
          items".
        </LegalReviewNote>
      </Section>

      <Section title="5. Your rights">
        <Typography variant="body2" paragraph>
          Under the DPDP Act, you have the right to:
        </Typography>
        <List dense disablePadding>
          <ListItem disableGutters><ListItemText primary="Access - request a copy of the personal data we hold about you." /></ListItem>
          <ListItem disableGutters><ListItemText primary="Correction - ask us to correct inaccurate or outdated data." /></ListItem>
          <ListItem disableGutters><ListItemText primary="Erasure - ask us to delete your personal data, subject to any legal retention we're required to keep." /></ListItem>
          <ListItem disableGutters><ListItemText primary="Withdraw consent - withdraw any consent you previously gave (e.g. marketing) at any time, as easily as you gave it." /></ListItem>
          <ListItem disableGutters><ListItemText primary="Grievance redressal - raise a complaint about how we've handled your data." /></ListItem>
        </List>
        <Box sx={{ mt: 2 }}>
          <Button component={RouterLink} to="/data-rights-request" variant="contained">
            Submit a Data Rights Request
          </Button>
        </Box>
      </Section>

      <Section title="6. Grievance Officer">
        <Typography variant="body2" paragraph>
          If you have a complaint or concern about how we've handled your personal data, you can
          contact our Grievance Officer:
        </Typography>
        <Typography variant="body2">{GRIEVANCE_OFFICER.name}, {GRIEVANCE_OFFICER.designation}</Typography>
        <Typography variant="body2">Email: {GRIEVANCE_OFFICER.email}</Typography>
        <Typography variant="body2">Phone: {GRIEVANCE_OFFICER.phone}</Typography>
        <Typography variant="body2" sx={{ mb: 1.5 }}>Address: {GRIEVANCE_OFFICER.address}</Typography>
        <Typography variant="body2" color="text.secondary">{DATA_PROTECTION_BOARD_NOTE}</Typography>
        <LegalReviewNote>
          [LEGAL REVIEW] Grievance Officer identity and contact details are placeholders - must be
          filled in with a real appointed person/team before publishing.
        </LegalReviewNote>
      </Section>

      <Section title="7. Security">
        <Typography variant="body2" paragraph>
          We use industry-standard practices such as password hashing to protect your account. See
          our Terms & Conditions for more on our security commitments.
        </Typography>
        <LegalReviewNote>
          [ENGINEERING NOTE - do not remove before this is fixed] An internal security review found
          gaps that should be closed before this notice makes any stronger security claim
          (e.g. "encrypted", "bank-grade security"): no CAPTCHA/bot-protection on login or
          registration, no field-level encryption of stored personal data, no enforced HTTPS in the
          current deployment configuration, and admin diagnostic endpoints that are not properly
          access-restricted. Full detail in DPDP_PROGRESS.md "Security gaps flagged".
        </LegalReviewNote>
      </Section>
    </LegalPageLayout>
  )
}
