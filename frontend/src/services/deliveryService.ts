import axiosClient from './axiosClient'
import type {
  Assignment, AssignmentSearchParams, CreateRouteRequest, DelayAssignmentRequest, DeliveryRoute,
  DeliverySummary, ManualAssignRequest, Partner, RouteSearchParams, UpdateAssignmentStatusRequest,
  UpdateRouteRequest, UpdateRouteStatusRequest,
} from '../types/delivery.types'
import type { ApiResponse, PageResponse } from '../types/common.types'

const BASE = '/delivery/assignments'

export const deliveryService = {
  // GET /delivery/assignments/search - keyword/status/date-range filters. Admin sees all;
  // non-admin (a DELIVERY_PARTNER) is scoped server-side to their own DeliveryPartner profile -
  // see DeliveryAssignmentServiceImpl.resolvePartnerId. A CUSTOMER calling this has no partner
  // profile and gets a 404 (ResourceNotFoundException), which is why the "Delivery" nav entry is
  // restricted to admin/DELIVERY_PARTNER roles only, not shown to customers.
  search: (params: AssignmentSearchParams) =>
    axiosClient.get<ApiResponse<PageResponse<Assignment>>>(`${BASE}/search`, {
      params: { page: 0, size: 20, sort: 'assignedAt,desc', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Assignment>>(`${BASE}/${id}`),

  // GET /delivery/assignments/order/{orderId} - ownership-scoped: admin sees any order's
  // assignment, a DELIVERY_PARTNER only their own, a CUSTOMER only their own order's assignment
  // (extended for real-time tracking - assignment.customerId is now reliably populated for both
  // auto- and manually-created assignments). Anyone else gets an empty list. Used by Order
  // Details' Delivery section and the customer tracking page, one request, not per-row.
  getByOrder: (orderId: string) =>
    axiosClient.get<ApiResponse<Assignment[]>>(`${BASE}/order/${orderId}`),

  // FARM_MANAGER/SUPER_ADMIN only on the backend (see DeliveryAssignmentController.manualAssign).
  assign: (data: ManualAssignRequest) =>
    axiosClient.post<ApiResponse<Assignment>>(BASE, data),

  updateStatus: (id: string, data: UpdateAssignmentStatusRequest) =>
    axiosClient.patch<ApiResponse<Assignment>>(`${BASE}/${id}/status`, data),

  // Notification-only - does not persist any field on the assignment (see backend: markDelayed
  // is a readOnly transaction). Sends a "running late" signal, nothing more.
  markDelayed: (id: string, data: DelayAssignmentRequest) =>
    axiosClient.post<ApiResponse<Assignment>>(`${BASE}/${id}/delay`, data),

  // SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER only. Single real aggregate the backend exposes -
  // just today's completed-delivery count. Per-status counts on the dashboard are derived from
  // search()'s own totalElements for each filter, not fabricated client-side.
  getSummary: () =>
    axiosClient.get<ApiResponse<DeliverySummary>>(`${BASE}/summary`),
}

export const deliveryPartnerService = {
  // FARM_MANAGER/SUPER_ADMIN only (DeliveryPartnerController.findAll) - narrower than
  // deliveryService's isAdmin(), which now also includes DELIVERY_MANAGER; DELIVERY_MANAGER
  // cannot list partners and therefore cannot use the Assign Delivery dialog.
  search: (params: { page?: number; size?: number } = {}) =>
    axiosClient.get<ApiResponse<PageResponse<Partner>>>('/delivery/partners', {
      params: { page: 0, size: 50, sort: 'name', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<Partner>>(`/delivery/partners/${id}`),
}

const ROUTES_BASE = '/delivery/routes'

export const deliveryRouteService = {
  // Open to any authenticated caller (DeliveryRouteController.search has no @PreAuthorize).
  // Pass active: true for an active-only list (Assign Delivery's route dropdown); omit it for
  // the admin Route Management table, which shows both active and inactive routes.
  search: (params: RouteSearchParams = {}) =>
    axiosClient.get<ApiResponse<PageResponse<DeliveryRoute>>>(`${ROUTES_BASE}/search`, {
      params: { page: 0, size: 50, sort: 'routeCode', ...params },
    }),

  getById: (id: string) =>
    axiosClient.get<ApiResponse<DeliveryRoute>>(`${ROUTES_BASE}/${id}`),

  // FARM_MANAGER/SUPER_ADMIN only on the backend (see DeliveryRouteController).
  create: (data: CreateRouteRequest) =>
    axiosClient.post<ApiResponse<DeliveryRoute>>(ROUTES_BASE, data),

  update: (id: string, data: UpdateRouteRequest) =>
    axiosClient.put<ApiResponse<DeliveryRoute>>(`${ROUTES_BASE}/${id}`, data),

  updateStatus: (id: string, data: UpdateRouteStatusRequest) =>
    axiosClient.patch<ApiResponse<DeliveryRoute>>(`${ROUTES_BASE}/${id}/status`, data),

  // 409 if the route is currently referenced by an active (ASSIGNED/OUT_FOR_DELIVERY) assignment
  // - the real server error is surfaced to the caller, not swallowed.
  delete: (id: string) =>
    axiosClient.delete<ApiResponse<void>>(`${ROUTES_BASE}/${id}`),
}
