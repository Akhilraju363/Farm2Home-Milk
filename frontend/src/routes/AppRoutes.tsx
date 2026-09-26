import { Routes, Route, Navigate } from 'react-router-dom'
import { MainLayout } from '../layouts/MainLayout'
import { ProtectedRoute } from '../components/common/ProtectedRoute'
import { RoleProtectedRoute } from '../components/common/RoleProtectedRoute'
import { AuthThemeScope } from '../components/common/AuthThemeScope'
import { LoginPage } from '../pages/auth/LoginPage'
import { RegisterPage } from '../pages/auth/RegisterPage'
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage'
import { DashboardPage } from '../pages/dashboard/DashboardPage'
import { CustomersPage } from '../pages/customers/CustomersPage'
import { CustomerDetailsPage } from '../pages/customers/CustomerDetailsPage'
import { SubscriptionsPage } from '../pages/subscriptions/SubscriptionsPage'
import { SubscriptionDetailsPage } from '../pages/subscriptions/SubscriptionDetailsPage'
import { OrdersPage } from '../pages/orders/OrdersPage'
import { OrderDetailsPage } from '../pages/orders/OrderDetailsPage'
import { CartPage } from '../pages/cart/CartPage'
import { CheckoutPage } from '../pages/checkout/CheckoutPage'
import { DeliveryDashboardPage } from '../pages/delivery/DeliveryDashboardPage'
import { DeliveryDetailsPage } from '../pages/delivery/DeliveryDetailsPage'
import { MyDeliveriesPage } from '../pages/delivery/MyDeliveriesPage'
import { AdminTrackingPage } from '../pages/delivery/AdminTrackingPage'
import { RouteManagementPage } from '../pages/delivery/RouteManagementPage'
import { TrackingPage } from '../pages/orders/TrackingPage'
import { PaymentsPage } from '../pages/payments/PaymentsPage'
import { PaymentDetailsPage } from '../pages/payments/PaymentDetailsPage'
import { PaymentReportsPage } from '../pages/payments/PaymentReportsPage'
import { PaymentAnalyticsPage } from '../pages/payments/PaymentAnalyticsPage'
import { WalletPage } from '../pages/wallet/WalletPage'
import { InventoryPage } from '../pages/inventory/InventoryPage'
import { ProductsRouteSwitch } from '../pages/products/ProductsRouteSwitch'
import { ProductDetailsRouteSwitch } from '../pages/products/ProductDetailsRouteSwitch'
import { CategoriesPage } from '../pages/products/CategoriesPage'
import { ProductionPage } from '../pages/production/ProductionPage'
import { ReportsPage } from '../pages/reports/ReportsPage'
import { NotificationsPage } from '../pages/notifications/NotificationsPage'
import { FarmsPage } from '../pages/settings/FarmsPage'
import { SettingsOverviewPage } from '../pages/settings/SettingsOverviewPage'
import { LocationMasterOverviewPage } from '../pages/settings/LocationMasterOverviewPage'
import { LocationMasterListPage } from '../pages/settings/LocationMasterListPage'
import { InvoicesPage } from '../pages/invoices/InvoicesPage'
import { InvoiceDetailsPage } from '../pages/invoices/InvoiceDetailsPage'
import { PrivacyPolicyPage } from '../pages/legal/PrivacyPolicyPage'
import { TermsPage } from '../pages/legal/TermsPage'
import { CookiePolicyPage } from '../pages/legal/CookiePolicyPage'
import { RefundPolicyPage } from '../pages/legal/RefundPolicyPage'
import { DataRightsRequestPage } from '../pages/legal/DataRightsRequestPage'
import { useAuth } from '../hooks/useAuth'
import { getLandingRoute } from '../utils/roleLanding'

/** Used for "/" and the catch-all "*" route - same role-based destination as the post-login/
 *  post-registration redirect (see roleLanding.ts), so a CUSTOMER/DELIVERY_PARTNER landing here
 *  via a bookmark or unknown URL doesn't hit the admin Dashboard either. Resolves to
 *  getLandingRoute(undefined) === "/dashboard" while logged out, same as before - ProtectedRoute
 *  still redirects an unauthenticated visitor to /login from there. */
function RoleHomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={getLandingRoute(user?.roles)} replace />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<RoleHomeRedirect />} />

      {/* Legal/compliance pages - reachable whether or not the visitor is logged in (DPDP
          requires the grievance/data-rights channel to work without an account), so these sit
          outside both ProtectedRoute and MainLayout, same as the auth routes below. Not wrapped
          in AuthThemeScope - they follow the app's normal light/dark toggle. */}
      <Route path="/privacy" element={<PrivacyPolicyPage />} />
      <Route path="/terms" element={<TermsPage />} />
      <Route path="/cookie-policy" element={<CookiePolicyPage />} />
      <Route path="/refund-policy" element={<RefundPolicyPage />} />
      <Route path="/data-rights-request" element={<DataRightsRequestPage />} />

      {/* Public auth routes - always light-themed regardless of the app-wide dark mode toggle,
          see AuthThemeScope */}
      <Route element={<AuthThemeScope />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      </Route>

      {/* Protected main app routes */}
      <Route element={<ProtectedRoute />}>
        <Route element={<MainLayout />}>
          <Route path="/dashboard"     element={<DashboardPage />} />

          {/* GET /customers, /search, /export are SUPER_ADMIN/DELIVERY_MANAGER only on the
              backend (see CustomerController) - narrower than Products' role set above, since
              FARM_MANAGER has no customer-list access at all. GET/PUT /customers/{id} itself has
              no backend role restriction beyond ownership, but this whole screen is only useful
              to callers who can also list/search, so it's gated the same as the rest. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'DELIVERY_MANAGER']} />}>
            <Route path="/customers"     element={<CustomersPage />} />
            <Route path="/customers/:id" element={<CustomerDetailsPage />} />
          </Route>

          {/* Unlike Products/Customers, this page is intentionally NOT role-gated - the backend
              itself supports self-service (a CUSTOMER sees/manages only their own subscriptions,
              enforced server-side by SubscriptionController/Service), so every authenticated role
              gets at least that. Admin-only affordances (customer column, customer selector on
              create) are conditionally rendered inside the page based on the caller's role. */}
          <Route path="/subscriptions"     element={<SubscriptionsPage />} />
          <Route path="/subscriptions/:id" element={<SubscriptionDetailsPage />} />
          {/* Also intentionally not role-gated, same rationale as Subscriptions above - the
              backend self-scopes every order endpoint to the caller's own orders for non-admins
              (see OrderController), so any authenticated role has at least that. */}
          <Route path="/orders"        element={<OrdersPage />} />
          <Route path="/orders/:id"    element={<OrderDetailsPage />} />

          {/* Cart/Checkout are CUSTOMER-only - the backend's CartController/orders/checkout don't
              themselves restrict by role (self-scoped to the caller's own id like Orders above),
              but a cart only makes sense for a shopping customer, so this is proactively gated
              here rather than relying on Sidebar visibility alone (see the explicit direct-URL
              access requirement this satisfies). */}
          <Route element={<RoleProtectedRoute allowedRoles={['CUSTOMER']} />}>
            <Route path="/cart"     element={<CartPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
          </Route>

          {/* /delivery is the admin/ops dashboard - GET/PATCH assignment endpoints have no
              @PreAuthorize (self-scoped server-side), but a plain CUSTOMER hitting this would
              404 immediately (no DeliveryPartner profile), so it's proactively gated here to
              admin roles only, same rationale as Customers above. /delivery/:id itself is left
              open (not role-gated) since both admins and the owning DELIVERY_PARTNER need it. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/delivery"      element={<DeliveryDashboardPage />} />
          </Route>
          <Route path="/delivery/:id"  element={<DeliveryDetailsPage />} />
          <Route element={<RoleProtectedRoute allowedRoles={['DELIVERY_PARTNER']} />}>
            <Route path="/delivery/my-deliveries" element={<MyDeliveriesPage />} />
          </Route>
          {/* Admin/ops live-tracking view - same role set as the dashboard above (GET
              /delivery/assignments/search has no @PreAuthorize, but this is an internal ops
              screen). /orders/:id/tracking is the customer-facing counterpart, not role-gated for
              the same self-service reason as Orders/Payments (delivery-service ownership-scopes
              GET .../location itself). */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/delivery/tracking" element={<AdminTrackingPage />} />
          </Route>
          {/* Route CRUD is FARM_MANAGER/SUPER_ADMIN only on the backend (DeliveryRouteController's
              write endpoints) - narrower than Delivery/Delivery Tracking above, matching
              DeliveryPartner management and manualAssign. A literal "/delivery/routes" path always
              wins over the "/delivery/:id" dynamic route below regardless of declaration order
              (React Router ranks static segments above params), so this doesn't need to be
              declared before it. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER']} />}>
            <Route path="/delivery/routes" element={<RouteManagementPage />} />
          </Route>
          <Route path="/orders/:id/tracking" element={<TrackingPage />} />
          {/* Not role-gated, same self-service rationale as Subscriptions/Orders above -
              GET /payments and GET /wallets/** self-scope server-side for every role; a
              DELIVERY_PARTNER hitting either just sees an empty result (they're not a customer),
              not an error. Nav visibility (not route access) is what actually keeps Payments off
              DELIVERY_PARTNER's sidebar and Wallet off the admin sidebar - see Sidebar.tsx. */}
          <Route path="/payments"      element={<PaymentsPage />} />
          <Route path="/payments/:id"  element={<PaymentDetailsPage />} />
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/payments/reports"   element={<PaymentReportsPage />} />
            <Route path="/payments/analytics" element={<PaymentAnalyticsPage />} />
          </Route>
          <Route path="/wallet"        element={<WalletPage />} />

          {/* Inventory (farm supplies: feed/medicine/equipment) is a separate domain from Product
              Management (see product.types.ts) - gated to the same admin roles as Products below,
              since it's an internal ops screen, not something a plain CUSTOMER/DELIVERY_PARTNER
              account should reach even though the backend's list/search/export/get endpoints use
              isAuthenticated() (any role). This matches the role set already required by this
              module's own /summary, /transactions/reports, and /analytics/consumption-trend
              endpoints (SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER). */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/inventory" element={<InventoryPage />} />
          </Route>

          {/* /products and /products/:id are shared with the customer Shop (see
              ProductsRouteSwitch/ProductDetailsRouteSwitch - CUSTOMER gets a read-only catalog
              view, admin roles get the unchanged Product Management screen). DELIVERY_PARTNER is
              still excluded, same rationale as Inventory above. Category management stays
              admin-only in its own, narrower block below - there's no customer-facing reason to
              reach /products/categories. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER', 'CUSTOMER']} />}>
            <Route path="/products"     element={<ProductsRouteSwitch />} />
            <Route path="/products/:id" element={<ProductDetailsRouteSwitch />} />
          </Route>
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/products/categories" element={<CategoriesPage />} />
          </Route>

          {/* Milk Production records are internal ops data - same rationale/role set as Inventory
              above (list/get endpoints are isAuthenticated(), but /summary/today, /reports, and
              /analytics/production-trend are already SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER
              only on the backend). */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/production" element={<ProductionPage />} />
          </Route>

          {/* Reports & Analytics aggregates cross-service business data (including company-wide
              milk production via productionService.getDailySummary(), which is isAuthenticated()
              on the backend with no per-customer scoping) - same admin role set as Payment
              Reports/Analytics above, not self-service data like Orders/Payments/Subscriptions. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/reports" element={<ReportsPage />} />
          </Route>

          <Route path="/notifications" element={<NotificationsPage />} />

          {/* Admin Settings overview and Farm & Business: GET /farm has no role restriction on the
              backend, but this is an internal ops/settings screen, gated the same as
              Products/Notifications above. DELIVERY_MANAGER is included here (read-only within
              FarmsPage itself via its own canWrite check) to preserve their existing documented
              access - do not narrow this to match Location Master's stricter set below. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/settings" element={<SettingsOverviewPage />} />
            <Route path="/settings/farm-business" element={<FarmsPage />} />
          </Route>

          {/* Location Master (India state/district/city reference-data CRUD) is genuinely
              SUPER_ADMIN/FARM_MANAGER only - matches LocationMasterController's own
              @PreAuthorize, narrower than Farm & Business above. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER']} />}>
            <Route path="/settings/location-master" element={<LocationMasterOverviewPage />} />
            <Route path="/settings/location-master/states" element={<LocationMasterListPage type="states" />} />
            <Route path="/settings/location-master/districts" element={<LocationMasterListPage type="districts" />} />
            <Route path="/settings/location-master/cities" element={<LocationMasterListPage type="cities" />} />
          </Route>

          {/* Not role-gated, same self-service rationale as Payments/Orders above - GET
              /invoices/me self-scopes for a CUSTOMER, while GET /invoices (search/list) is
              FARM_MANAGER/SUPER_ADMIN only on the backend; InvoicesPage branches internally on
              which one to call. A DELIVERY_PARTNER hitting this just sees an empty "My Invoices"
              result (they're not a customer), not an error. */}
          <Route path="/invoices"     element={<InvoicesPage />} />
          <Route path="/invoices/:id" element={<InvoiceDetailsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<RoleHomeRedirect />} />
    </Routes>
  )
}
