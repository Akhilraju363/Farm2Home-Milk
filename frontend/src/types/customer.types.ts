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
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  pincode: string
  defaultAddress: boolean
  createdAt: string
}

export interface CreateAddressRequest {
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  pincode: string
}
