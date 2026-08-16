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
  routeName: string
  status: AssignmentStatus
  // true when OrderEventConsumer created this automatically (least-loaded eligible partner on
  // the order's own route, see PartnerSelectionServiceImpl); false for an admin's manualAssign.
  autoAssigned: boolean
  // The assigned partner's current (live, not a snapshot) count of non-terminal
  // (ASSIGNED/OUT_FOR_DELIVERY) assignments, including this one.
  partnerActiveDeliveries: number
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
  // Live count of non-terminal (ASSIGNED/OUT_FOR_DELIVERY) assignments - the same workload
  // PartnerSelectionServiceImpl uses for automatic least-loaded assignment.
  activeDeliveries?: number
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
  // Coverage circle used for automatic route selection (DeliveryRouteSelectionServiceImpl in
  // customer-service, the sole authoritative implementation - the frontend never computes this).
  // Null if this route was never configured for it.
  centerLatitude?: number | null
  centerLongitude?: number | null
  radiusKm?: number | null
}

export interface CreateRouteRequest {
  routeName: string
  routeCode: string
  area: string
  city: string
  pincode: string
  centerLatitude?: number
  centerLongitude?: number
  radiusKm?: number
}

// Partial update - every field optional/omit to leave unchanged (matches UpdateRouteRequest on
// the backend). routeCode cannot be changed via this endpoint.
export interface UpdateRouteRequest {
  routeName?: string
  area?: string
  city?: string
  pincode?: string
  active?: boolean
  centerLatitude?: number
  centerLongitude?: number
  radiusKm?: number
}

export interface UpdateRouteStatusRequest {
  active: boolean
}

export interface RouteSearchParams {
  keyword?: string
  active?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface ManualAssignRequest {
  orderId: string
  deliveryPartnerId: string
  // Optional explicit override - the order's own automatically-selected route
  // (Order.deliveryRouteId) is used when this is omitted. See AssignDeliveryDialog.
  routeId?: string
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
  // true = automatically assigned only; false = manually assigned only; omit = both. Used by the
  // admin dashboard's "Automatically assigned"/"Manually assigned" KPI counts (search().totalElements,
  // same pattern already used for per-status counts - no bespoke count endpoint).
  autoAssigned?: boolean
  page?: number
  size?: number
  sort?: string
}
