export interface Customer {
  id: string
  customerCode: string
  firstName: string
  lastName: string
  mobile: string
  email?: string
  status: CustomerStatus
  profileImageUrl?: string | null
  createdAt: string
}

export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'

export const CUSTOMER_STATUS_LABELS: Record<CustomerStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  SUSPENDED: 'Suspended',
}

// Every field optional - null/omitted means "leave unchanged" (matches UpdateCustomerRequest on
// the backend; mobile/customerCode are immutable and not settable here).
export interface UpdateCustomerRequest {
  firstName?: string
  lastName?: string
  email?: string
  status?: CustomerStatus
}

export interface CustomerAddress {
  id: string
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  district?: string
  pincode: string
  defaultAddress: boolean
  createdAt: string
}

export interface CreateAddressRequest {
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  district?: string
  pincode: string
}

export interface UpdateAddressRequest {
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  district?: string
  pincode?: string
}

export interface CustomerSearchParams {
  keyword?: string
  status?: CustomerStatus
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
  sort?: string
}
