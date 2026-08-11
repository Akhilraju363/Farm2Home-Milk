import {
  Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  Toolbar, Typography, Box, Divider, Tooltip, Button, Menu, MenuItem, CircularProgress,
} from '@mui/material'
import {
  Dashboard, People, Subscriptions, ShoppingCart, Payment,
  Inventory, Agriculture, BarChart, WaterDrop, Download, Logout, Notifications, Storefront,
  LocalShipping, AccountBalanceWallet, Receipt, TrendingUp, ReceiptLong, GpsFixed, Settings,
} from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useSnackbar } from 'notistack'
import { useAuth } from '../../hooks/useAuth'
import { reportsService, REPORT_TYPES, type ReportType } from '../../services/reportsService'
import { downloadBlob } from '../../utils/download'

export const DRAWER_WIDTH = 240

const NAV_ITEMS = [
  { label: 'Dashboard',     icon: <Dashboard />,     path: '/dashboard' },
  // GET /customers, /search, and /export are SUPER_ADMIN/DELIVERY_MANAGER only on the backend
  // (see CustomerController) - narrower than the general isAdmin() (which also covers
  // FARM_MANAGER), so this needs its own explicit role list rather than the adminOnly flag below.
  { label: 'Customers',     icon: <People />,         path: '/customers', roles: ['SUPER_ADMIN', 'DELIVERY_MANAGER'] },
  // Sellable catalog (Product/ProductCategory) - a separate domain from Inventory below (farm
  // supplies), gated the same way as Notifications since it's staff-only, not customer-facing.
  { label: 'Products',      icon: <Storefront />,     path: '/products', adminOnly: true },
  { label: 'Subscriptions', icon: <Subscriptions />,  path: '/subscriptions' },
  { label: 'Orders',        icon: <ShoppingCart />,   path: '/orders' },
  // GET /delivery/assignments is open server-side but 404s for a CUSTOMER (no DeliveryPartner
  // profile to resolve), so both entries are explicitly role-gated rather than shown to everyone.
  { label: 'Delivery',      icon: <LocalShipping />,  path: '/delivery', roles: ['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER'] },
  // Live map of currently OUT_FOR_DELIVERY assignments - same role set as Delivery above, since
  // it's the same admin/ops audience, just a map-focused view instead of the search table.
  { label: 'Delivery Tracking', icon: <GpsFixed />,   path: '/delivery/tracking', roles: ['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER'] },
  { label: 'My Deliveries', icon: <LocalShipping />,  path: '/delivery/my-deliveries', roles: ['DELIVERY_PARTNER'] },
  // GET /payments self-scopes for a CUSTOMER, but a DELIVERY_PARTNER isn't a customer and has
  // no payments of their own either - explicitly excluded rather than shown to everyone
  // authenticated, per the same "don't show nav merely because the user is authenticated"
  // rationale as My Deliveries above.
  { label: 'Payments',      icon: <Payment />,        path: '/payments', roles: ['CUSTOMER', 'SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER'] },
  // Wallet is customer-facing self-service only - deliberately not shown under admin roles at all.
  { label: 'Wallet',        icon: <AccountBalanceWallet />, path: '/wallet', roles: ['CUSTOMER'] },
  { label: 'Payment Reports',   icon: <Receipt />,     path: '/payments/reports', roles: ['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER'] },
  { label: 'Payment Analytics', icon: <TrendingUp />,  path: '/payments/analytics', roles: ['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER'] },
  // GET /invoices (search/list) is FARM_MANAGER/SUPER_ADMIN only (narrower than Payments' role
  // set - DELIVERY_MANAGER has no invoice access); GET /invoices/me self-scopes for a CUSTOMER.
  // Excluded for DELIVERY_MANAGER/DELIVERY_PARTNER, same "don't show nav merely because the user
  // is authenticated" rationale as My Deliveries/Wallet above.
  { label: 'Invoices',      icon: <ReceiptLong />,    path: '/invoices', roles: ['CUSTOMER', 'SUPER_ADMIN', 'FARM_MANAGER'] },
  { label: 'Inventory',     icon: <Inventory />,      path: '/inventory' },
  { label: 'Production',    icon: <WaterDrop />,      path: '/production' },
  { label: 'Reports',       icon: <BarChart />,       path: '/reports' },
  // Customers/delivery partners still reach their own notifications via the bell's "View all"
  // link (self-scoped /notifications/me) - this sidebar entry is just the ops-wide browsing
  // path, so it's restricted the same way the other admin-only pages here effectively are.
  { label: 'Notifications', icon: <Notifications />,  path: '/notifications', adminOnly: true },
  // Settings is intentionally NOT listed here - it lives in the bottom section next to Logout
  // (see SETTINGS_ITEM/DrawerContent below), not mixed in with the main navigation list.
]

// Admin Settings currently has exactly one real backend surface (FarmController) - see the Admin
// Settings backend audit. GET /farm has no role restriction server-side, but this is gated the
// same as Products/Notifications above (adminOnly -> isAdmin(): SUPER_ADMIN/FARM_MANAGER/
// DELIVERY_MANAGER) rather than opened to every authenticated role. Rendered in its own bottom
// section, immediately above Logout, rather than inside NAV_ITEMS.
const SETTINGS_ITEM = { label: 'Settings', icon: <Settings />, path: '/settings/farms' }

interface Props {
  mobileOpen: boolean
  onClose: () => void
}

function DownloadReportsButton() {
  const { enqueueSnackbar } = useSnackbar()
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const [downloading, setDownloading] = useState<string | null>(null)

  const handleDownload = async (report: ReportType) => {
    setAnchorEl(null)
    setDownloading(report.path)
    try {
      const { blob, filename } = await reportsService.export(report, 'CSV')
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar(`Couldn't download ${report.label}. Please try again.`, { variant: 'error' })
    } finally {
      setDownloading(null)
    }
  }

  return (
    <>
      <Button
        fullWidth
        variant="contained"
        color="primary"
        startIcon={downloading ? <CircularProgress size={16} color="inherit" /> : <Download />}
        onClick={(e) => setAnchorEl(e.currentTarget)}
        disabled={!!downloading}
        sx={{ borderRadius: 1.5, textTransform: 'none', py: 1 }}
      >
        Download Reports
      </Button>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
        {REPORT_TYPES.map((r) => (
          <MenuItem key={r.path} onClick={() => handleDownload(r)}>
            {r.label} (CSV)
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}

function DrawerContent() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { logoutUser, isAdmin, user } = useAuth()
  const admin = isAdmin()
  const navItems = NAV_ITEMS.filter((item) => {
    if ('roles' in item && item.roles) return user?.roles.some((r) => (item.roles as string[]).includes(r)) ?? false
    return !item.adminOnly || admin
  })

  const handleLogout = () => {
    logoutUser()
    navigate('/login')
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ gap: 1 }}>
        <Agriculture sx={{ color: 'primary.main', fontSize: 28 }} />
        <Box>
          <Typography variant="subtitle1" fontWeight={700} lineHeight={1.2} color="primary.main">
            Farm2Home
          </Typography>
          <Typography variant="caption" color="text.secondary" lineHeight={1}>
            Milk
          </Typography>
        </Box>
      </Toolbar>
      <Divider />
      <List sx={{ px: 1, pt: 1 }}>
        {navItems.map((item) => {
          const active = pathname === item.path || pathname.startsWith(item.path + '/')
          return (
            <Tooltip key={item.path} title="" placement="right">
              <ListItemButton
                selected={active}
                onClick={() => navigate(item.path)}
                sx={{
                  borderRadius: 1.5,
                  mb: 0.5,
                  '&.Mui-selected': {
                    bgcolor: 'primary.main',
                    color: 'white',
                    '& .MuiListItemIcon-root': { color: 'white' },
                    '&:hover': { bgcolor: 'primary.dark' },
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 14 }} />
              </ListItemButton>
            </Tooltip>
          )
        })}
      </List>

      <Box sx={{ mt: 'auto', p: 1.5 }}>
        <DownloadReportsButton />
        <Divider sx={{ my: 1.5 }} />
        {admin && (
          <ListItemButton
            selected={pathname.startsWith('/settings')}
            onClick={() => navigate(SETTINGS_ITEM.path)}
            sx={{
              borderRadius: 1.5,
              mb: 0.5,
              '&.Mui-selected': {
                bgcolor: 'primary.main',
                color: 'white',
                '& .MuiListItemIcon-root': { color: 'white' },
                '&:hover': { bgcolor: 'primary.dark' },
              },
            }}
          >
            <ListItemIcon sx={{ minWidth: 36 }}>{SETTINGS_ITEM.icon}</ListItemIcon>
            <ListItemText primary={SETTINGS_ITEM.label} primaryTypographyProps={{ fontSize: 14 }} />
          </ListItemButton>
        )}
        <ListItemButton onClick={handleLogout} sx={{ borderRadius: 1.5, color: 'error.main' }}>
          <ListItemIcon sx={{ minWidth: 36, color: 'error.main' }}><Logout fontSize="small" /></ListItemIcon>
          <ListItemText primary="Logout" primaryTypographyProps={{ fontSize: 14 }} />
        </ListItemButton>
      </Box>
    </Box>
  )
}

export function Sidebar({ mobileOpen, onClose }: Props) {
  return (
    <Box component="nav" sx={{ width: { sm: DRAWER_WIDTH }, flexShrink: { sm: 0 } }}>
      {/* Mobile */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onClose}
        ModalProps={{ keepMounted: true }}
        sx={{ display: { xs: 'block', sm: 'none' }, '& .MuiDrawer-paper': { width: DRAWER_WIDTH } }}
      >
        <DrawerContent />
      </Drawer>
      {/* Desktop */}
      <Drawer
        variant="permanent"
        sx={{ display: { xs: 'none', sm: 'block' }, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
        open
      >
        <DrawerContent />
      </Drawer>
    </Box>
  )
}
