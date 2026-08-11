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
import { DeliveryDashboardPage } from '../pages/delivery/DeliveryDashboardPage'
import { DeliveryDetailsPage } from '../pages/delivery/DeliveryDetailsPage'
import { MyDeliveriesPage } from '../pages/delivery/MyDeliveriesPage'
import { AdminTrackingPage } from '../pages/delivery/AdminTrackingPage'
import { TrackingPage } from '../pages/orders/TrackingPage'
import { PaymentsPage } from '../pages/payments/PaymentsPage'
import { PaymentDetailsPage } from '../pages/payments/PaymentDetailsPage'
import { PaymentReportsPage } from '../pages/payments/PaymentReportsPage'
import { PaymentAnalyticsPage } from '../pages/payments/PaymentAnalyticsPage'
import { WalletPage } from '../pages/wallet/WalletPage'
import { InventoryPage } from '../pages/inventory/InventoryPage'
import { ProductsPage } from '../pages/products/ProductsPage'
import { ProductDetailsPage } from '../pages/products/ProductDetailsPage'
import { CategoriesPage } from '../pages/products/CategoriesPage'
import { ProductionPage } from '../pages/production/ProductionPage'
import { ReportsPage } from '../pages/reports/ReportsPage'
import { NotificationsPage } from '../pages/notifications/NotificationsPage'
import { FarmsPage } from '../pages/settings/FarmsPage'
import { InvoicesPage } from '../pages/invoices/InvoicesPage'
import { InvoiceDetailsPage } from '../pages/invoices/InvoiceDetailsPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />

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
          <Route path="/inventory"     element={<InventoryPage />} />

          {/* Product Management is a separate domain from Inventory (see product.types.ts) -
              gated to the same admin roles as Notifications below, since it's an internal ops
              screen, not something a plain CUSTOMER/DELIVERY_PARTNER account should reach even
              though the backend itself allows any authenticated role to read products. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/products"            element={<ProductsPage />} />
            <Route path="/products/categories" element={<CategoriesPage />} />
            <Route path="/products/:id"        element={<ProductDetailsPage />} />
          </Route>

          <Route path="/production"    element={<ProductionPage />} />
          <Route path="/reports"       element={<ReportsPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />

          {/* Admin Settings currently has exactly one real backend surface (FarmController) -
              see the Admin Settings backend audit. GET /farm has no role restriction on the
              backend, but this is an internal ops/settings screen, gated the same as
              Products/Notifications above rather than exposed to every authenticated role. */}
          <Route element={<RoleProtectedRoute allowedRoles={['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER']} />}>
            <Route path="/settings/farms" element={<FarmsPage />} />
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

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
