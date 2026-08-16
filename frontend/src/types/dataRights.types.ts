// DPDP Act 2023 data-rights/grievance requests. Mirrors customer-service's enums exactly.

export type DataRightsRequestType = 'ACCESS' | 'CORRECTION' | 'ERASURE' | 'WITHDRAW_CONSENT' | 'GRIEVANCE' | 'OTHER'

export const DATA_RIGHTS_REQUEST_TYPE_LABELS: Record<DataRightsRequestType, string> = {
  ACCESS: 'Access a copy of my personal data',
  CORRECTION: 'Correct inaccurate/outdated personal data',
  ERASURE: 'Erase/delete my personal data',
  WITHDRAW_CONSENT: 'Withdraw consent I previously gave',
  GRIEVANCE: 'General grievance / complaint',
  OTHER: 'Something else',
}

export type DataRightsRequestStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED'

export const DATA_RIGHTS_REQUEST_STATUS_LABELS: Record<DataRightsRequestStatus, string> = {
  NEW: 'New',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  REJECTED: 'Rejected',
}

export interface CreateDataRightsRequest {
  requesterName: string
  requesterContact: string
  requestType: DataRightsRequestType
  details?: string
}

export interface DataRightsRequest {
  id: string
  customerId?: string | null
  requesterName: string
  requesterContact: string
  requestType: DataRightsRequestType
  details?: string | null
  status: DataRightsRequestStatus
  resolutionNotes?: string | null
  createdAt: string
  updatedAt: string
}
