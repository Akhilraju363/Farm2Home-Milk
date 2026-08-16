export interface Farm {
  id: string
  farmName: string
  ownerName: string
  location?: string
  description?: string
  imageUrl?: string
  createdAt: string
}

export interface CreateFarmRequest {
  farmName: string
  ownerName: string
  location?: string
  description?: string
}

export interface UpdateFarmRequest {
  farmName?: string
  ownerName?: string
  location?: string
  description?: string
}

export interface FarmSearchParams {
  keyword?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

// Farm2Home's own delivery origin + radius - distinct from Farm above (the supplier-farm
// registry). Singleton - there is exactly one of these, never a list.
export interface BusinessSettings {
  farmLatitude: number
  farmLongitude: number
  deliveryRadiusKm: number
  // Farm2Home's real business address - display-only, not used in the delivery-eligibility
  // calculation (that's farmLatitude/farmLongitude exclusively). The single authoritative source
  // for this text anywhere in the app - never duplicated/hardcoded elsewhere (e.g. on a route).
  businessName?: string | null
  addressLine?: string | null
  locality?: string | null
  city?: string | null
  district?: string | null
  state?: string | null
  pincode?: string | null
  updatedAt: string
}

export interface UpdateBusinessSettingsRequest {
  farmLatitude: number
  farmLongitude: number
  deliveryRadiusKm: number
}
