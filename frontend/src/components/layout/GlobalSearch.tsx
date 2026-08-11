import {
  Box, InputBase, Paper, Popper, ClickAwayListener, List, ListItemButton,
  ListItemText, Typography, CircularProgress, Chip,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { customerService } from '../../services/customerService'
import { orderService } from '../../services/orderService'
import { formatCurrency, statusColor } from '../../utils/formatters'

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 350

function useDebounced(value: string, delayMs: number): string {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

export function GlobalSearch() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const anchorRef = useRef<HTMLDivElement>(null)
  const debouncedQuery = useDebounced(query, DEBOUNCE_MS)
  const searchReady = debouncedQuery.trim().length >= MIN_QUERY_LENGTH

  // customer-service's /search is restricted to SUPER_ADMIN/DELIVERY_MANAGER server-side (not
  // the same set as useAuth().isAdmin(), which also includes FARM_MANAGER) - gated separately so
  // a FARM_MANAGER account doesn't fire a request that's guaranteed to 403. Order search has no
  // such restriction: order-service self-scopes it to the caller's own orders for every
  // non-admin role (see OrderController.search), so it's safe and useful for every role,
  // including CUSTOMER and DELIVERY_PARTNER.
  const canSearchCustomers = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'DELIVERY_MANAGER') ?? false

  const { data: customerData, isFetching: customersLoading } = useQuery({
    queryKey: ['global-search', 'customers', debouncedQuery],
    queryFn: () => customerService.search(debouncedQuery.trim()),
    enabled: searchReady && canSearchCustomers,
  })
  const { data: orderData, isFetching: ordersLoading } = useQuery({
    queryKey: ['global-search', 'orders', debouncedQuery],
    queryFn: () => orderService.search({ keyword: debouncedQuery.trim(), size: 5 }),
    enabled: searchReady,
  })

  const customers = canSearchCustomers ? (customerData?.data.data.content ?? []) : []
  const orders = orderData?.data.data.content ?? []
  const loading = (canSearchCustomers && customersLoading) || ordersLoading
  const hasResults = customers.length > 0 || orders.length > 0

  const goTo = (path: string) => {
    setOpen(false)
    setQuery('')
    navigate(path)
  }

  return (
    <ClickAwayListener onClickAway={() => setOpen(false)}>
      <Box ref={anchorRef} sx={{ position: 'relative', width: { xs: '100%', sm: 420 } }}>
        <Paper
          variant="outlined"
          sx={{
            display: 'flex', alignItems: 'center', px: 1.5, py: 0.75,
            borderRadius: 2, bgcolor: 'action.hover', borderColor: 'transparent',
          }}
        >
          <SearchIcon fontSize="small" sx={{ color: 'text.secondary', mr: 1 }} />
          <InputBase
            placeholder="Global Search..."
            fullWidth
            value={query}
            onChange={(e) => { setQuery(e.target.value); setOpen(true) }}
            onFocus={() => query && setOpen(true)}
            sx={{ fontSize: 14 }}
          />
        </Paper>

        <Popper open={open && searchReady} anchorEl={anchorRef.current} placement="bottom-start" sx={{ zIndex: 1300, width: anchorRef.current?.offsetWidth }}>
          <Paper elevation={4} sx={{ mt: 0.5, maxHeight: 420, overflowY: 'auto' }}>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                <CircularProgress size={22} />
              </Box>
            ) : !hasResults ? (
              <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 3, textAlign: 'center' }}>
                No matches for &quot;{debouncedQuery}&quot;
              </Typography>
            ) : (
              <>
                {customers.length > 0 && (
                  <>
                    <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ px: 2, pt: 1.5, display: 'block' }}>
                      CUSTOMERS
                    </Typography>
                    <List disablePadding dense>
                      {customers.map((c) => (
                        <ListItemButton key={c.id} onClick={() => goTo('/customers')}>
                          <ListItemText
                            primary={`${c.firstName} ${c.lastName}`}
                            secondary={`${c.customerCode} • ${c.mobile}${c.email ? ' • ' + c.email : ''}`}
                          />
                          <Chip label={c.status} size="small" color={statusColor(c.status)} sx={{ fontSize: 10 }} />
                        </ListItemButton>
                      ))}
                    </List>
                  </>
                )}
                {orders.length > 0 && (
                  <>
                    <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ px: 2, pt: 1.5, display: 'block' }}>
                      ORDERS
                    </Typography>
                    <List disablePadding dense>
                      {orders.map((o) => (
                        <ListItemButton key={o.id} onClick={() => goTo(`/orders/${o.id}`)}>
                          <ListItemText
                            primary={o.orderNumber}
                            secondary={formatCurrency(o.totalAmount)}
                          />
                          <Chip label={o.status} size="small" color={statusColor(o.status)} sx={{ fontSize: 10 }} />
                        </ListItemButton>
                      ))}
                    </List>
                  </>
                )}
              </>
            )}
          </Paper>
        </Popper>
      </Box>
    </ClickAwayListener>
  )
}
