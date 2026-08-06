import {
  Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  Toolbar, Typography, Box, Divider, Tooltip, Button, Menu, MenuItem, CircularProgress,
} from '@mui/material'
import {
  Dashboard, People, Subscriptions, ShoppingCart, Payment,
  Inventory, Agriculture, BarChart, WaterDrop, Download, Logout, Notifications,
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
  { label: 'Customers',     icon: <People />,         path: '/customers' },
  { label: 'Subscriptions', icon: <Subscriptions />,  path: '/subscriptions' },
  { label: 'Orders',        icon: <ShoppingCart />,   path: '/orders' },
  { label: 'Payments',      icon: <Payment />,        path: '/payments' },
  { label: 'Inventory',     icon: <Inventory />,      path: '/inventory' },
  { label: 'Production',    icon: <WaterDrop />,      path: '/production' },
  { label: 'Reports',       icon: <BarChart />,       path: '/reports' },
  // Customers/delivery partners still reach their own notifications via the bell's "View all"
  // link (self-scoped /notifications/me) - this sidebar entry is just the ops-wide browsing
  // path, so it's restricted the same way the other admin-only pages here effectively are.
  { label: 'Notifications', icon: <Notifications />,  path: '/notifications', adminOnly: true },
]

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
  const { logoutUser, isAdmin } = useAuth()
  const admin = isAdmin()
  const navItems = NAV_ITEMS.filter((item) => !item.adminOnly || admin)

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
