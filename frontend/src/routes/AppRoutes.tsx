import { Routes, Route, Navigate } from 'react-router-dom'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      {/* Auth routes */}
      <Route path="/login" element={<div>Login Page</div>} />
      <Route path="/register" element={<div>Register Page</div>} />
      <Route path="/verify-otp" element={<div>OTP Verification Page</div>} />
      <Route path="/forgot-password" element={<div>Forgot Password Page</div>} />

      {/* Customer routes */}
      <Route path="/customer/dashboard" element={<div>Customer Dashboard</div>} />
      <Route path="/customer/subscriptions" element={<div>Subscriptions</div>} />
      <Route path="/customer/orders" element={<div>Orders</div>} />
      <Route path="/customer/payments" element={<div>Payments</div>} />
      <Route path="/customer/profile" element={<div>Profile</div>} />

      {/* Admin routes */}
      <Route path="/admin/dashboard" element={<div>Admin Dashboard</div>} />
      <Route path="/admin/customers" element={<div>Customers</div>} />
      <Route path="/admin/cows" element={<div>Cows</div>} />
      <Route path="/admin/production" element={<div>Production</div>} />
      <Route path="/admin/inventory" element={<div>Inventory</div>} />
      <Route path="/admin/deliveries" element={<div>Deliveries</div>} />
      <Route path="/admin/reports" element={<div>Reports</div>} />

      {/* Delivery Partner routes */}
      <Route path="/delivery/today" element={<div>Today's Deliveries</div>} />
      <Route path="/delivery/:id" element={<div>Delivery Details</div>} />

      <Route path="*" element={<div>404 Not Found</div>} />
    </Routes>
  )
}
