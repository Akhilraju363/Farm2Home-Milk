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
  // Null when this address has no captured location yet (see DeliveryAvailability - a real
  // device/map position, never derived from city/pincode).
  latitude?: number | null
  longitude?: number | null
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
  latitude?: number
  longitude?: number
}

export interface UpdateAddressRequest {
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  district?: string
  pincode?: string
  latitude?: number
  longitude?: number
}

// Matches order-service/customer-service's DeliveryAvailabilityResponse exactly.
// deliveryAvailable is null (not false) when it cannot be determined - no address, or the
// address has no captured coordinates yet. Never treat null as either available or unavailable.
export interface DeliveryAvailability {
  deliveryAvailable: boolean | null
  distanceKm: number | null
  deliveryRadiusKm: number
  message: string | null
  // Automatically-selected delivery route for this address (DeliveryRouteSelectionServiceImpl).
  // Only routeName is meant for customer display - internal route codes are deliberately not
  // exposed here. Null when unavailable/unknown, or a route-coverage gap.
  routeId?: string | null
  routeName?: string | null
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
