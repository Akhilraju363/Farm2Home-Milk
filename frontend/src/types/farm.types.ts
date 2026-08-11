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
