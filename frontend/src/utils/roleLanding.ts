import type { Role } from '../types/auth.types'

/** Where a freshly-authenticated user should land, based on role - used right after login/
 *  registration only. Route access itself is still enforced by RoleProtectedRoute regardless of
 *  where this sends someone; this just avoids dropping DELIVERY_PARTNER/CUSTOMER onto the admin
 *  Dashboard, whose APIs are SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER-only. */
export function getLandingRoute(roles: Role[] | undefined): string {
  if (roles?.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER' || r === 'DELIVERY_MANAGER')) {
    return '/dashboard'
  }
  if (roles?.includes('DELIVERY_PARTNER')) return '/delivery/my-deliveries'
  // /products is shared with admin Product Management - ProductsRouteSwitch/ProductDetailsRouteSwitch
  // (see AppRoutes.tsx) render the customer Shop experience instead of the admin page for this role.
  if (roles?.includes('CUSTOMER')) return '/products'
  return '/dashboard'
}
