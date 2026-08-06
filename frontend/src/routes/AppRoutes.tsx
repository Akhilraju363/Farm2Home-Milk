import { Routes, Route, Navigate } from 'react-router-dom'
import { MainLayout } from '../layouts/MainLayout'
import { ProtectedRoute } from '../components/common/ProtectedRoute'
import { LoginPage } from '../pages/auth/LoginPage'
import { RegisterPage } from '../pages/auth/RegisterPage'
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage'
import { DashboardPage } from '../pages/dashboard/DashboardPage'
import { CustomersPage } from '../pages/customers/CustomersPage'
import { SubscriptionsPage } from '../pages/subscriptions/SubscriptionsPage'
import { OrdersPage } from '../pages/orders/OrdersPage'
import { PaymentsPage } from '../pages/payments/PaymentsPage'
import { InventoryPage } from '../pages/inventory/InventoryPage'
import { ProductionPage } from '../pages/production/ProductionPage'
import { ReportsPage } from '../pages/reports/ReportsPage'
import { NotificationsPage } from '../pages/notifications/NotificationsPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />

      {/* Public auth routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      {/* Protected main app routes */}
      <Route element={<ProtectedRoute />}>
        <Route element={<MainLayout />}>
          <Route path="/dashboard"     element={<DashboardPage />} />
          <Route path="/customers"     element={<CustomersPage />} />
          <Route path="/subscriptions" element={<SubscriptionsPage />} />
          <Route path="/orders"        element={<OrdersPage />} />
          <Route path="/payments"      element={<PaymentsPage />} />
          <Route path="/inventory"     element={<InventoryPage />} />
          <Route path="/production"    element={<ProductionPage />} />
          <Route path="/reports"       element={<ReportsPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
