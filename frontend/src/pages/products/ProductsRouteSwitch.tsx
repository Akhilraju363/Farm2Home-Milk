import { useAuth } from '../../hooks/useAuth'
import { ProductsPage } from './ProductsPage'
import { ShopPage } from './ShopPage'

/** /products is shared by both audiences - CUSTOMER gets the read-only Shop experience, every
 *  other authenticated role (SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER, per the existing
 *  RoleProtectedRoute on this route in AppRoutes.tsx) gets the existing admin Product Management
 *  page, unchanged. Same URL, same Sidebar path, different component - see Sidebar.tsx's two
 *  NAV_ITEMS entries ("Shop" vs "Products") for the matching label split. */
export function ProductsRouteSwitch() {
  const { user } = useAuth()
  const isCustomer = user?.roles.includes('CUSTOMER') ?? false
  return isCustomer ? <ShopPage /> : <ProductsPage />
}
