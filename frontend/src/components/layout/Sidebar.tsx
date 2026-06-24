import {
  Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  Toolbar, Typography, Box, Divider, Tooltip,
} from '@mui/material'
import {
  Dashboard, People, Subscriptions, ShoppingCart, Payment,
  Inventory, Agriculture, BarChart, WaterDrop,
} from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'

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
]

interface Props {
  mobileOpen: boolean
  onClose: () => void
}

function DrawerContent() {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return (
    <>
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
        {NAV_ITEMS.map((item) => {
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
    </>
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
