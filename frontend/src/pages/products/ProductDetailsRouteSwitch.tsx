import { useAuth } from '../../hooks/useAuth'
import { ProductDetailsPage } from './ProductDetailsPage'
import { ShopProductDetailsPage } from './ShopProductDetailsPage'

/** /products/:id counterpart to ProductsRouteSwitch - see that file's comment. */
export function ProductDetailsRouteSwitch() {
  const { user } = useAuth()
  const isCustomer = user?.roles.includes('CUSTOMER') ?? false
  return isCustomer ? <ShopProductDetailsPage /> : <ProductDetailsPage />
}
