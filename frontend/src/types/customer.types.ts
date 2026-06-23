export interface Customer {
  id: string
  customerCode: string
  firstName: string
  lastName: string
  mobile: string
  email?: string
  status: CustomerStatus
}

export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'

export interface CustomerAddress {
  id: string
  customerId: string
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  pincode: string
  latitude?: number
  longitude?: number
  isDefault: boolean
}
