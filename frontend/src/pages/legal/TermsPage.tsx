import { Typography, Box } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { LegalPageLayout } from '../../components/legal/LegalPageLayout'
import { LegalReviewNote } from '../../components/legal/LegalReviewNote'
import { COMPANY_LEGAL_NAME } from '../../constants/legal'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" fontWeight={700} gutterBottom>{title}</Typography>
      {children}
    </Box>
  )
}

/** DRAFT - no Terms & Conditions page existed in this app before this change (confirmed by
 *  codebase audit - see DPDP_PROGRESS.md). This is a minimal skeleton covering the service itself
 *  plus the data-protection clause DPDP compliance specifically asked for; it is not a complete
 *  commercial Terms of Service and must be reviewed/expanded by counsel before publishing. */
export function TermsPage() {
  return (
    <LegalPageLayout title="Terms & Conditions" lastUpdated="DRAFT - not yet published">
      <LegalReviewNote>
        This entire page is an engineering-drafted DRAFT pending legal review. No Terms &
        Conditions page existed in this application before this change - this is a minimal
        skeleton, not a complete commercial agreement. It has not been approved by counsel.
      </LegalReviewNote>

      <Section title="1. Acceptance of terms">
        <Typography variant="body2">
          By creating a Farm2Home account or placing an order, you agree to these Terms &
          Conditions and to our{' '}
          <Typography component={RouterLink} to="/privacy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Privacy Notice
          </Typography>.
        </Typography>
        <LegalReviewNote>
          [LEGAL REVIEW] Standard sections a commercial Terms of Service normally needs and that
          are NOT drafted here yet: eligibility, order/subscription terms, pricing and payment
          terms, cancellation/refund policy, delivery obligations, liability limitations, dispute
          resolution/governing law, and account termination. Only the data-protection clause below
          was written as part of this DPDP compliance pass - the rest needs to be authored
          separately.
        </LegalReviewNote>
      </Section>

      <Section title="2. Data protection">
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
    </LegalPageLayout>
  )
}
