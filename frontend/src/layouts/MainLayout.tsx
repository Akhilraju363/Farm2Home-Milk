import { Box, Toolbar } from '@mui/material'
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar, DRAWER_WIDTH } from '../components/layout/Sidebar'
import { TopBar } from '../components/layout/TopBar'

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':     'Dashboard',
  '/customers':     'Customer Management',
  '/subscriptions': 'Subscription Management',
  '/orders':        'Orders',
  '/payments':      'Payments',
  '/inventory':     'Inventory',
  '/products':      'Product Management',
  '/products/categories': 'Category Management',
  '/production':    'Milk Production',
  '/reports':       'Reports & Analytics',
  '/delivery':      'Delivery Management',
  '/delivery/my-deliveries': 'My Deliveries',
  '/payments/reports':   'Payment Reports',
  '/payments/analytics': 'Payment Analytics',
  '/wallet':        'My Wallet',
  '/settings/farms': 'Farm & Business Information',
  '/invoices':      'Invoices',
  '/delivery/tracking': 'Delivery Tracking',
}

export function MainLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const { pathname } = useLocation()
  // /products/:id, /customers/:id, /subscriptions/:id aren't in the static map above (they carry
  // a variable id) - detected separately rather than adding every id to PAGE_TITLES.
  const isProductDetails = pathname.startsWith('/products/') && pathname !== '/products/categories'
  const isCustomerDetails = pathname.startsWith('/customers/')
  const isSubscriptionDetails = pathname.startsWith('/subscriptions/')
  const isOrderTracking = pathname.endsWith('/tracking') && pathname.startsWith('/orders/')
  const isOrderDetails = pathname.startsWith('/orders/') && !isOrderTracking
  const isDeliveryDetails = pathname.startsWith('/delivery/')
    && pathname !== '/delivery/my-deliveries' && pathname !== '/delivery/tracking'
  const isPaymentDetails = pathname.startsWith('/payments/')
    && pathname !== '/payments/reports' && pathname !== '/payments/analytics'
  const isInvoiceDetails = pathname.startsWith('/invoices/')
  const title = PAGE_TITLES[pathname]
    ?? (isProductDetails ? 'Product Details'
        : isCustomerDetails ? 'Customer Details'
        : isSubscriptionDetails ? 'Subscription Details'
        : isOrderTracking ? 'Delivery Tracking'
        : isOrderDetails ? 'Order Details'
        : isDeliveryDetails ? 'Delivery Details'
        : isPaymentDetails ? 'Payment Details'
        : isInvoiceDetails ? 'Invoice Details'
        : 'Farm2Home Milk')

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <TopBar onMenuClick={() => setMobileOpen(true)} title={title} />
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
          bgcolor: 'background.default',
          minHeight: '100vh',
          p: { xs: 2, md: 3 },
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}
