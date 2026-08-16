// Single source of truth for grievance-officer/company details referenced from the footer,
// Privacy Notice, Terms, and the data-rights form's confirmation copy - so they can't drift out of
// sync. Every value below is a placeholder pending legal/business sign-off - see DPDP_PROGRESS.md
// "Needs lawyer/business review" for the full list of what must be confirmed before this ships to
// real users.

export const COMPANY_LEGAL_NAME = '[LEGAL REVIEW: entity name, e.g. "Farm2Home Milk Private Limited"]'

export const GRIEVANCE_OFFICER = {
  name: '[LEGAL REVIEW: Grievance Officer name]',
  designation: 'Grievance Officer',
  email: '[LEGAL REVIEW: grievance@farm2homemilk.example]',
  phone: '[LEGAL REVIEW: phone number]',
  address: '[LEGAL REVIEW: registered postal address]',
}

export const DATA_PROTECTION_BOARD_NOTE =
  'A data principal dissatisfied with how a grievance was handled may also approach the Data ' +
  'Protection Board of India.'

export const PRIVACY_NOTICE_VERSION = 'privacy-notice-v1-DRAFT'
