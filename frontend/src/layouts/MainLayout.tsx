import { Box, Toolbar } from '@mui/material'
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar, DRAWER_WIDTH } from '../components/layout/Sidebar'
import { TopBar } from '../components/layout/TopBar'

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':     'Dashboard',
  '/customers':     'Customers',
  '/subscriptions': 'Subscriptions',
  '/orders':        'Orders',
  '/payments':      'Payments',
  '/inventory':     'Inventory',
  '/production':    'Milk Production',
  '/reports':       'Reports & Analytics',
}

export function MainLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const { pathname } = useLocation()
  const title = PAGE_TITLES[pathname] ?? 'Farm2Home Milk'

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
