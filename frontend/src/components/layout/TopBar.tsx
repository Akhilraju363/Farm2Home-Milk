import {
  AppBar, Toolbar, IconButton, Typography, Box,
  Avatar, Menu, MenuItem, Divider, Tooltip, Badge, Chip, ListItemIcon,
} from '@mui/material'
import {
  Menu as MenuIcon, Logout, Notifications, DarkMode, LightMode,
  Sms, Email, PhoneIphone, ShoppingCartOutlined,
} from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../hooks/useAuth'
import { useThemeMode } from '../../contexts/ThemeModeContext'
import { notificationService } from '../../services/notificationService'
import { cartService } from '../../services/cartService'
import { formatDateTime } from '../../utils/formatters'
import type { NotificationChannel, NotificationStatus } from '../../types/notification.types'
import { DRAWER_WIDTH } from './Sidebar'
import { GlobalSearch } from './GlobalSearch'

const CHANNEL_ICONS: Record<NotificationChannel, React.ReactNode> = {
  SMS: <Sms fontSize="small" />,
  EMAIL: <Email fontSize="small" />,
  PUSH: <PhoneIphone fontSize="small" />,
}

const STATUS_COLOR: Record<NotificationStatus, 'success' | 'warning' | 'error'> = {
  SENT: 'success',
  PENDING: 'warning',
  FAILED: 'error',
}

function NotificationBell() {
  const { isAdmin } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [anchor, setAnchor] = useState<null | HTMLElement>(null)
  const admin = isAdmin()

  // Admin roles see the ops-wide feed (every recipient); everyone else sees only their own
  // notifications, via a self-scoped endpoint that can never expose another user's data -
  // see notificationService for why these aren't interchangeable.
  const { data } = useQuery({
    queryKey: ['notifications', admin ? 'summary' : 'mine'],
    queryFn: () => (admin ? notificationService.getSummary(10) : notificationService.getMine(10)),
    refetchInterval: 60_000,
  })
  const notifications = data?.data.data ?? []
  // Read/unread is a self-scoped concept - admin's feed spans other recipients' notifications,
  // so their own "read" state on those rows doesn't mean "has the admin seen this" the way it
  // does for a customer's own inbox. Badge stays a plain recent-count for admin.
  const badgeCount = admin ? notifications.length : notifications.filter((n) => !n.read).length

  const markOne = useMutation({
    mutationFn: (id: string) => notificationService.markAsRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  return (
    <>
      <Tooltip title="Recent notifications">
        <IconButton onClick={(e) => setAnchor(e.currentTarget)} size="small" sx={{ mr: 1 }} aria-label="Recent notifications">
          <Badge badgeContent={badgeCount} color="error">
            <Notifications />
          </Badge>
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={() => setAnchor(null)}
        slotProps={{ paper: { sx: { width: 360, maxHeight: 420 } } }}
      >
        <Typography variant="subtitle2" fontWeight={700} sx={{ px: 2, pt: 1, pb: 0.5 }}>
          {admin ? 'Recent Notifications' : 'Your Notifications'}
        </Typography>
        <Divider />
        {notifications.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 3, textAlign: 'center' }}>
            Nothing recent
          </Typography>
        ) : (
          notifications.map((n) => (
            <MenuItem
              key={n.id}
              onClick={() => { if (!admin && !n.read) markOne.mutate(n.id); setAnchor(null) }}
              sx={{ whiteSpace: 'normal', py: 1, bgcolor: !admin && !n.read ? 'action.hover' : 'transparent' }}
            >
              <ListItemIcon sx={{ minWidth: 32 }}>{CHANNEL_ICONS[n.channel]}</ListItemIcon>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="body2" noWrap fontWeight={!admin && !n.read ? 700 : 400}>{n.subject || n.recipient}</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(n.createdAt)}</Typography>
              </Box>
              <Chip label={n.status} size="small" color={STATUS_COLOR[n.status]} sx={{ ml: 1, fontSize: 10 }} />
            </MenuItem>
          ))
        )}
        {/* Admin's dropdown preview is the ops-wide /summary feed - the full Notifications page
            only shows the caller's own notifications (/me), so linking there for admin would
            silently switch datasets mid-navigation. Only link out for the self-scoped view. */}
        {!admin && (
          <>
            <Divider />
            <MenuItem onClick={() => { setAnchor(null); navigate('/notifications') }} sx={{ justifyContent: 'center' }}>
              <Typography variant="body2" color="primary.main" fontWeight={600}>View all</Typography>
            </MenuItem>
          </>
        )}
      </Menu>
    </>
  )
}

/** Quick-access cart affordance from anywhere in the app - CUSTOMER only, mirrors the Cart nav
 *  item's own role gate (see Sidebar.tsx). Shares the exact same ['cart'] query key as CartPage/
 *  ShopProductCard's add-to-cart mutations, so the badge count updates automatically whenever
 *  those invalidate it - no separate polling needed. */
function CartButton() {
  const navigate = useNavigate()
  const { data } = useQuery({
    queryKey: ['cart'],
    queryFn: () => cartService.getCart(),
    staleTime: 30_000,
  })
  const itemCount = data?.data.data.itemCount ?? 0

  return (
    <Tooltip title="Cart">
      <IconButton onClick={() => navigate('/cart')} size="small" sx={{ mr: 1 }} aria-label="Cart">
        <Badge badgeContent={itemCount} color="error">
          <ShoppingCartOutlined />
        </Badge>
      </IconButton>
    </Tooltip>
  )
}

interface Props {
  onMenuClick: () => void
  title: string
}

export function TopBar({ onMenuClick, title }: Props) {
  const { user, logoutUser, isCustomer } = useAuth()
  const { mode, toggleMode } = useThemeMode()
  const navigate = useNavigate()
  const [anchor, setAnchor] = useState<null | HTMLElement>(null)

  const initials = user
    ? `${user.username?.charAt(0) ?? ''}`.toUpperCase()
    : 'U'

  const handleLogout = () => {
    setAnchor(null)
    logoutUser()
    navigate('/login')
  }

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
        ml: { sm: `${DRAWER_WIDTH}px` },
        bgcolor: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        color: 'text.primary',
      }}
    >
      <Toolbar>
        <IconButton edge="start" onClick={onMenuClick} sx={{ mr: 2, display: { sm: 'none' } }} aria-label="Open navigation menu">
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" fontWeight={600} sx={{ mr: 3, display: { xs: 'none', md: 'block' } }}>
          {title}
        </Typography>

        <GlobalSearch />

        <Box sx={{ flexGrow: 1 }} />

        {isCustomer() && <CartButton />}

        <NotificationBell />

        <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
          <IconButton
            onClick={toggleMode}
            size="small"
            sx={{ mr: 1 }}
            aria-label={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {mode === 'dark' ? <LightMode /> : <DarkMode />}
          </IconButton>
        </Tooltip>

        <Tooltip title="Account">
          <IconButton onClick={(e) => setAnchor(e.currentTarget)} size="small" aria-label="Account menu">
            <Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.main', fontSize: 14 }}>
              {initials}
            </Avatar>
          </IconButton>
        </Tooltip>
        <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
          <MenuItem disabled sx={{ opacity: '1 !important' }}>
            <Box>
              <Typography variant="body2" fontWeight={600}>{user?.username ?? 'User'}</Typography>
              <Typography variant="caption" color="text.secondary">{user?.mobile}</Typography>
            </Box>
          </MenuItem>
          <Divider />
          <MenuItem onClick={handleLogout}>
            <Logout fontSize="small" sx={{ mr: 1 }} /> Logout
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  )
}
