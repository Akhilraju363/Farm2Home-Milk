import {
  Box, TextField, MenuItem, InputAdornment, IconButton, Tooltip, Button, Typography, Paper,
  Pagination, Alert, CircularProgress, Chip, Tabs, Tab,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { Search, Refresh, Add, Close, Download, ShoppingCart } from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { NewOrderDialog } from '../../components/order/NewOrderDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { orderService } from '../../services/orderService'
import { downloadBlob } from '../../utils/download'
import { formatCurrency, formatDate, statusColor } from '../../utils/formatters'
import { MILK_TYPE_LABELS, MILK_TYPES, ORDER_STATUS_LABELS, ORDER_TYPE_LABELS, orderItemLabel } from '../../types/order.types'
import type { Order, OrderStatus } from '../../types/order.types'

const PAGE_SIZE = 20
const DEBOUNCE_MS = 400

const STATUS_TABS: Array<{ value: OrderStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

export function OrdersPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  // Matches order-service's own UserPrincipal.isAdmin() exactly (FARM_MANAGER + DELIVERY_MANAGER +
  // SUPER_ADMIN). None of the order endpoints an admin needs here (list/search/get/create-for-
  // others/export) carry a narrower @PreAuthorize, so this one check covers all of them.
  const canManage = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER' || r === 'DELIVERY_MANAGER') ?? false

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [milkTypeFilter, setMilkTypeFilter] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [exporting, setExporting] = useState(false)
  const [formOpen, setFormOpen] = useState(false)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const hasActiveFilters = Boolean(debouncedSearch || status || milkTypeFilter || dateFrom || dateTo)

  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    status: (status || undefined) as OrderStatus | undefined,
    milkType: (milkTypeFilter || undefined) as any,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page, size: PAGE_SIZE,
  }), [debouncedSearch, status, milkTypeFilter, dateFrom, dateTo, page])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['orders', 'search', searchParams],
    queryFn: () => orderService.search(searchParams),
  })

  const orders = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['orders'] })
  const resetFilters = () => { setSearch(''); setStatus(''); setMilkTypeFilter(''); setDateFrom(''); setDateTo(''); setPage(0) }

  const handleExport = async () => {
    setExporting(true)
    try {
      const { blob, filename } = await orderService.export({ ...searchParams, format: 'CSV' })
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar("Couldn't export orders. Please try again.", { variant: 'error' })
    } finally {
      setExporting(false)
    }
  }

  const handleSaved = () => { setFormOpen(false); invalidate() }

  const columns: GridColDef<Order>[] = [
    { field: 'orderNumber', headerName: 'Order #', width: 150 },
    ...(canManage ? [{
      field: 'customerId', headerName: 'Customer', width: 120,
      renderCell: ({ value }: { value: string }) => (
        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{value.slice(0, 8)}…</Typography>
      ),
    } as GridColDef<Order>] : []),
    { field: 'orderDate', headerName: 'Date', width: 120, renderCell: ({ value }) => formatDate(value as string) },
    { field: 'orderType', headerName: 'Type', width: 120, renderCell: ({ value }) => ORDER_TYPE_LABELS[value as Order['orderType']] },
    {
      field: 'items', headerName: 'Items', width: 160, sortable: false,
      renderCell: ({ value }) => (value as Order['items'])?.map((it) => orderItemLabel(it)).join(', ') || '—',
    },
    { field: 'totalAmount', headerName: 'Amount', width: 110, renderCell: ({ value }) => formatCurrency(value as number) },
    {
      field: 'status', headerName: 'Status', width: 150,
      renderCell: ({ value }) => <Chip label={ORDER_STATUS_LABELS[value as OrderStatus]} size="small" color={statusColor(value as string)} sx={{ fontWeight: 600 }} />,
    },
  ]

  return (
    <Box>
      <PageHeader
        title="Orders"
        subtitle="Manage manual and subscription-generated Farm2Home milk delivery orders."
        action={{ label: 'New Order', icon: <Add />, onClick: () => setFormOpen(true) }}
      />

      <Tabs
        value={status} onChange={(_e, v) => { setStatus(v); setPage(0) }}
        sx={{ mb: 2, borderBottom: '1px solid', borderColor: 'divider' }}
      >
        {STATUS_TABS.map((t) => <Tab key={t.value} value={t.value} label={t.label} />)}
      </Tabs>

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search order number, notes…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          sx={{ minWidth: 240, flex: 1 }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
            endAdornment: search && (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearch('')}><Close fontSize="small" /></IconButton>
              </InputAdornment>
            ),
          }}
        />
        <TextField select size="small" label="Milk Type" value={milkTypeFilter}
          onChange={(e) => { setMilkTypeFilter(e.target.value); setPage(0) }} sx={{ minWidth: 150 }}>
          <MenuItem value="">All Types</MenuItem>
          {MILK_TYPES.map((m) => <MenuItem key={m} value={m}>{MILK_TYPE_LABELS[m]}</MenuItem>)}
        </TextField>
        <TextField
          size="small" label="From" type="date" value={dateFrom}
          onChange={(e) => { setDateFrom(e.target.value); setPage(0) }}
          InputLabelProps={{ shrink: true }} sx={{ minWidth: 150 }}
        />
        <TextField
          size="small" label="To" type="date" value={dateTo}
          onChange={(e) => { setDateTo(e.target.value); setPage(0) }}
          InputLabelProps={{ shrink: true }} sx={{ minWidth: 150 }}
        />
        <Button
          variant="outlined" startIcon={exporting ? <CircularProgress size={16} /> : <Download />}
          onClick={handleExport} disabled={exporting}
        >
          Export
        </Button>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Couldn't load orders. Please check your connection and try again.
        </Alert>
      ) : !isLoading && orders.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <ShoppingCart sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No orders match your search.' : 'No orders found.'}
          </Typography>
          {hasActiveFilters ? (
            <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>
          ) : (
            <Button variant="contained" startIcon={<Add />} onClick={() => setFormOpen(true)} sx={{ mt: 1 }}>New Order</Button>
          )}
        </Paper>
      ) : (
        <DataGrid
          rows={orders}
          columns={columns}
          loading={isLoading || isFetching}
          rowHeight={56}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          onRowClick={(params) => navigate(`/orders/${params.id}`)}
          sx={{
            bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
            '& .MuiDataGrid-row': { cursor: 'pointer' },
          }}
        />
      )}

      {!isLoading && !isError && totalElements > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} order{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}

      <NewOrderDialog
        open={formOpen} canSelectCustomer={canManage}
        onClose={() => setFormOpen(false)} onSaved={handleSaved}
      />
    </Box>
  )
}
