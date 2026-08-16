import { Box, Toolbar } from '@mui/material'
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar, DRAWER_WIDTH } from '../components/layout/Sidebar'
import { TopBar } from '../components/layout/TopBar'
import { PublicFooter } from '../components/layout/PublicFooter'
import { useAuth } from '../hooks/useAuth'

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':     'Dashboard',
  '/customers':     'Customer Management',
  '/subscriptions': 'Subscription Management',
  '/orders':        'Orders',
  '/payments':      'Payments',
  '/inventory':     'Inventory',
  // /products is CUSTOMER's Shop (see ProductsRouteSwitch) - overridden below for that role.
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
  '/delivery/routes': 'Route Management',
}

export function MainLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const { pathname } = useLocation()
  const { user } = useAuth()
  const isCustomer = user?.roles.includes('CUSTOMER') ?? false
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
  const title = (pathname === '/products' && isCustomer) ? 'Shop'
    : PAGE_TITLES[pathname]
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
          minWidth: 0,
          width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
          // Wide content (DataGrid tables in particular, whose summed column widths can exceed
          // the viewport on mobile) scrolls within this container instead of forcing the whole
          // page - including the fixed TopBar/Sidebar - to scroll horizontally. minWidth: 0 above
          // is the flexbox fix that lets this shrink below its children's intrinsic width at all.
          overflowX: 'auto',
          bgcolor: 'background.default',
          minHeight: '100vh',
          p: { xs: 2, md: 3 },
        }}
      >
        <Toolbar />
        <Outlet />
        <PublicFooter />
      </Box>
    </Box>
  )
}
