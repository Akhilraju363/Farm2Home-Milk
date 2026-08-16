// DPDP Act 2023 per-purpose consent. Mirrors customer-service's ConsentPurpose enum exactly.

export type ConsentPurpose = 'ESSENTIAL_SERVICE' | 'MARKETING_COMMUNICATIONS' | 'LOCATION_TRACKING' | 'ANALYTICS_COOKIES'

// [LEGAL REVIEW] Purpose descriptions shown next to each checkbox - must match the Privacy Notice
// wording exactly once legal has finalized it (see PrivacyPolicyPage.tsx).
export const CONSENT_PURPOSE_LABELS: Record<ConsentPurpose, string> = {
  ESSENTIAL_SERVICE: 'Process my account and order details to provide the Farm2Home service (required)',
  MARKETING_COMMUNICATIONS: 'Send me offers, new product updates, and other marketing communications',
  LOCATION_TRACKING: 'Use my delivery address location to show live delivery tracking',
  ANALYTICS_COOKIES: 'Allow non-essential analytics/tracking to help improve the app',
}

export interface ConsentRecord {
  purpose: ConsentPurpose
  granted: boolean
  noticeVersion?: string | null
  createdAt: string
  updatedAt: string
}

export interface ConsentChoice {
  purpose: ConsentPurpose
  granted: boolean
  noticeVersion?: string
}
