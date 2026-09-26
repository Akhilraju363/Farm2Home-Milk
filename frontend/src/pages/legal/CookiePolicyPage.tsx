import { Typography, List, ListItem, ListItemText, Box } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { LegalPageLayout } from '../../components/legal/LegalPageLayout'
import { LegalReviewNote } from '../../components/legal/LegalReviewNote'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" fontWeight={700} gutterBottom>{title}</Typography>
      {children}
    </Box>
  )
}

/** DRAFT - see DPDP_PROGRESS.md and the 2026-09-25 legal/accessibility audit. Built directly from
 *  a codebase audit of what actually runs client-side: a repo-wide search for
 *  document.cookie/localStorage/sessionStorage plus every third-party <script>/SDK the frontend
 *  loads (frontend/index.html, package.json, utils/googleMapsConfig.ts,
 *  components/delivery/TrackingMap.tsx, pages/auth/LoginPage.tsx). Farm2Home does not set any
 *  first- or third-party cookies of its own - "storage" here means browser localStorage/
 *  sessionStorage, and the two third-party scripts below are loaded directly from Google's own
 *  domains, not proxied through Farm2Home. */
export function CookiePolicyPage() {
  return (
    <LegalPageLayout title="Cookie Policy" lastUpdated="DRAFT - not yet published">
      <LegalReviewNote>
        This entire page is an engineering-drafted DRAFT pending legal review. It has not been
        approved by counsel. See DPDP_PROGRESS.md at the repo root for background.
      </LegalReviewNote>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        This page explains what Farm2Home actually stores in your browser and which third-party
        scripts your browser loads when you use the app - it is not generic boilerplate. This
        supplements our{' '}
        <Typography component={RouterLink} to="/privacy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
          Privacy Notice
        </Typography>.
      </Typography>

      <Section title="1. Farm2Home does not set cookies">
        <Typography variant="body2" paragraph>
          As of this audit, Farm2Home's own frontend does not set any browser cookies (via
          <code> document.cookie</code> or an HTTP <code>Set-Cookie</code> header). Where this page
          refers to a "cookie banner," it means the consent choice described in Section 3 below,
          which gates browser storage and future analytics, not cookies in the strict sense.
        </Typography>
      </Section>

      <Section title="2. What we do store in your browser">
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText
              primary="Sign-in session (strictly necessary)"
              secondary={'Your access/refresh tokens are stored in localStorage if you chose "Remember Me" at login, otherwise in sessionStorage (cleared when the tab closes). Needed to keep you signed in; the app cannot work without it.'}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Cookie/tracking consent choice (strictly necessary)"
              secondary={'A small record (key "f2h_tracker_consent") of whether you accepted or rejected non-essential tracking, so we don\'t ask again every visit.'}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Display preference (functional)"
              secondary="Your light/dark theme choice, so the app remembers it on your next visit."
            />
          </ListItem>
        </List>
        <LegalReviewNote>
          [ENGINEERING NOTE] This list reflects a repo-wide search for localStorage/sessionStorage
          usage at the time of this audit. If a future change adds a new browser-storage key, this
          section must be updated to match.
        </LegalReviewNote>
      </Section>

      <Section title="3. Third-party scripts your browser loads">
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText
              primary="Google Identity Services (Sign-In)"
              secondary={'Loaded on every page from accounts.google.com so you can use "Sign in with Google." This script runs whether or not you actually use Google Sign-In, and Google may set its own cookies/identifiers under google.com per its own policies - Farm2Home does not control that. If you use Google Sign-In, the credential it returns is sent to our servers to sign you in.'}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Google Maps JavaScript SDK"
              secondary={'Loaded only on delivery-tracking screens, and only when a Google Maps API key is configured for this deployment, to show live delivery location on a map. When it loads, your device/browser exchanges data directly with Google to render the map, per Google\'s own terms.'}
            />
          </ListItem>
        </List>
        <LegalReviewNote>
          [LEGAL REVIEW] Confirm this disclosure is sufficient, and whether Google Identity
          Services loading unconditionally (rather than only on the Login page) needs to be
          changed or additionally disclosed/consented to. Confirm whether Google Maps/Google
          Identity Services require their own DPA/data-sharing disclosure beyond what's written
          here.
        </LegalReviewNote>
      </Section>

      <Section title="4. Optional analytics/marketing trackers">
        <Typography variant="body2" paragraph>
          Farm2Home does not currently use any analytics, advertising, or marketing tracking
          service (no Google Analytics, Meta Pixel, Hotjar, or similar - confirmed by a repo-wide
          search). The consent banner shown across the app exists so that if we ever add one, it
          will not run until you accept it, and you can reject it just as easily. You can change
          your choice at any time by clearing this site's browser storage, which will show the
          banner again on your next visit.
        </Typography>
      </Section>

      <Section title="5. Payments">
        <Typography variant="body2" paragraph>
          When you pay online, our payment gateway (Razorpay) may load its own secure checkout
          script and set cookies/storage under its own domain to process the payment and prevent
          fraud - this is outside Farm2Home's control and governed by Razorpay's own policies.
        </Typography>
        <LegalReviewNote>
          [ENGINEERING NOTE] In this codebase's current configuration, the mock payment gateway is
          active and Razorpay's Checkout.js is not actually loaded by the frontend yet (see
          DPDP_PROGRESS.md). This section describes the behavior expected once a real Razorpay
          integration goes live, so it does not need to be rewritten at that point - but confirm
          it still matches whatever the live integration actually does before launch.
        </LegalReviewNote>
      </Section>
    </LegalPageLayout>
  )
}
