export type AssignmentStatus = 'ASSIGNED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED'
export const ASSIGNMENT_STATUS_LABELS: Record<AssignmentStatus, string> = {
  ASSIGNED: 'Assigned',
  OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
}

// Matches AssignmentStatus.canTransitionTo() in delivery-service exactly. DELIVERED/FAILED are
// terminal (no entry here) - there is no retry/reassignment path once either is reached.
export const ASSIGNMENT_STATUS_TRANSITIONS: Record<AssignmentStatus, AssignmentStatus[]> = {
  ASSIGNED: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  DELIVERED: [],
  FAILED: [],
}

// Mirrors delivery-service's AssignmentResponse exactly - no orderNumber/customerId (the entity
// carries them but the response DTO deliberately doesn't expose them; enrich from order-service
// on the details page only, never per-row in a list, to avoid N+1).
export interface Assignment {
  id: string
  orderId: string
  deliveryPartnerId: string
  deliveryPartnerName: string
  deliveryPartnerMobile: string
  routeId: string
  routeCode: string
  status: AssignmentStatus
  assignedAt: string
  deliveredAt?: string
  failureReason?: string
  deliveryProof?: string
  createdAt: string
}

export interface Partner {
  id: string
  userId: string
  routeId?: string
  routeCode?: string
  name: string
  mobile: string
  vehicleType?: string
  active: boolean
  createdAt: string
}

export interface DeliveryRoute {
  id: string
  routeName: string
  routeCode: string
  area: string
  city: string
  pincode: string
  active: boolean
  createdAt: string
}

export interface ManualAssignRequest {
  orderId: string
  deliveryPartnerId: string
  routeId: string
}

export interface UpdateAssignmentStatusRequest {
  status: AssignmentStatus
  failureReason?: string
  deliveryProof?: string
}

export interface DelayAssignmentRequest {
  reason: string
}

export interface DeliverySummary {
  completedDeliveriesToday: number
}

export interface AssignmentSearchParams {
  keyword?: string
  dateFrom?: string
  dateTo?: string
  status?: AssignmentStatus
  page?: number
  size?: number
  sort?: string
}
