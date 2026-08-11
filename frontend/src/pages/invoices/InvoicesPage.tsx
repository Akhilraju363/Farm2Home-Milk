import {
  Box, Chip, IconButton, Tooltip, Paper, Typography, TextField, InputAdornment, Button, Pagination,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { Refresh, Search, Close, Receipt } from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { CustomerAutocomplete } from '../../components/subscription/CustomerAutocomplete'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { invoiceService } from '../../services/invoiceService'
import { formatCurrency, formatDate, statusColor } from '../../utils/formatters'
import type { Customer } from '../../types/customer.types'
import type { InvoiceSummary } from '../../types/invoice.types'

const PAGE_SIZE = 20

// There is no invoice-specific status - the order's real status is shown instead (see the
// Phase 1 backend audit: no invoice status enum exists anywhere).
const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending', ASSIGNED: 'Assigned', OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED: 'Delivered', CANCELLED: 'Cancelled',
}

// GET /invoices (search/list) is FARM_MANAGER/SUPER_ADMIN only on the backend (see
// InvoiceController) - narrower than Payments' isAdmin() (which also covers DELIVERY_MANAGER),
// so this needs its own check rather than reusing useAuth().isAdmin().
export function InvoicesPage() {
  const { user } = useAuth()
  const canManage = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  return canManage ? <AdminInvoicesTable /> : <MyInvoicesList />
}

// ── Customer: plain list, no filters - GET /invoices/me supports pagination only. ────────────
function MyInvoicesList() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['invoices', 'mine', page],
    queryFn: () => invoiceService.getMine({ page, size: PAGE_SIZE }),
  })
  const invoices = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const columns: GridColDef<InvoiceSummary>[] = [
    { field: 'invoiceNumber', headerName: 'Invoice #', flex: 1, minWidth: 160 },
    {
      field: 'orderNumber', headerName: 'Order', width: 160,
      renderCell: ({ value }) => value || <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    { field: 'issueDate', headerName: 'Issue Date', width: 130, renderCell: ({ value }) => formatDate(value as string) },
    { field: 'totalAmount', headerName: 'Amount', width: 130, renderCell: ({ value }) => formatCurrency(value as number) },
    {
      field: 'orderStatus', headerName: 'Status', width: 150,
      renderCell: ({ value }) => value
        ? <Chip label={ORDER_STATUS_LABELS[value as string] ?? value} size="small" color={statusColor(value as string)} />
        : <Typography variant="body2" color="text.disabled">—</Typography>,
    },
  ]

  return (
    <Box>
      <PageHeader title="My Invoices" subtitle="Billing documents generated for your orders." />

      {!isLoading && invoices.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <Receipt sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700}>No invoices yet.</Typography>
        </Paper>
      ) : (
        <DataGrid
          rows={invoices}
          columns={columns}
          loading={isLoading || isFetching}
          rowHeight={56}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          onRowClick={(params) => navigate(`/invoices/${params.id}`)}
          sx={{
            bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
            '& .MuiDataGrid-row': { cursor: 'pointer' },
          }}
        />
      )}

      {!isLoading && invoices.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} invoice{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}
    </Box>
  )
}

// ── Admin: full search/filter table sourced from GET /invoices. ──────────────────────────────
function AdminInvoicesTable() {
  const navigate = useNavigate()

  const [search, setSearch] = useState('')
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [page, setPage] = useState(0)

  const debouncedSearch = useDebounced(search, 350)
  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    customerId: customer?.id || undefined,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page, size: PAGE_SIZE,
  }), [debouncedSearch, customer, dateFrom, dateTo, page])

  const hasActiveFilters = Boolean(debouncedSearch || customer || dateFrom || dateTo)
  const resetFilters = () => { setSearch(''); setCustomer(null); setDateFrom(''); setDateTo(''); setPage(0) }

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['invoices', 'search', searchParams],
    queryFn: () => invoiceService.search(searchParams),
  })
  const invoices = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0

  const columns: GridColDef<InvoiceSummary>[] = [
    { field: 'invoiceNumber', headerName: 'Invoice #', flex: 1, minWidth: 160 },
    {
      field: 'customerName', headerName: 'Customer', flex: 1, minWidth: 160,
      renderCell: ({ value }) => value || <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    { field: 'issueDate', headerName: 'Issue Date', width: 130, renderCell: ({ value }) => formatDate(value as string) },
    { field: 'totalAmount', headerName: 'Amount', width: 130, renderCell: ({ value }) => formatCurrency(value as number) },
    {
      field: 'orderStatus', headerName: 'Order Status', width: 160,
      renderCell: ({ value }) => value
        ? <Chip label={ORDER_STATUS_LABELS[value as string] ?? value} size="small" color={statusColor(value as string)} />
        : <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    {
      field: 'orderNumber', headerName: 'Order #', width: 160,
      renderCell: ({ value }) => value || <Typography variant="body2" color="text.disabled">—</Typography>,
    },
  ]

  return (
    <Box>
      <PageHeader title="Invoices" subtitle="Manage and track billing across all orders." />

      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          size="small"
          placeholder="Search invoice number…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          sx={{ minWidth: 220 }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
            endAdornment: search && (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearch('')}><Close fontSize="small" /></IconButton>
              </InputAdornment>
            ),
          }}
        />
        <Box sx={{ minWidth: 220 }}>
          <CustomerAutocomplete value={customer} onChange={(c) => { setCustomer(c); setPage(0) }} />
        </Box>
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
        {hasActiveFilters && <Button size="small" onClick={resetFilters}>Clear Filters</Button>}
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small" sx={{ ml: 'auto' }}><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {!isLoading && invoices.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <Receipt sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No invoices match your filters.' : 'No invoices have been generated yet.'}
          </Typography>
          {!hasActiveFilters && (
            <Typography variant="body2" color="text.secondary">
              Generate one from an order's details page.
            </Typography>
          )}
        </Paper>
      ) : (
        <DataGrid
          rows={invoices}
          columns={columns}
          loading={isLoading || isFetching}
          rowCount={totalElements}
          paginationMode="server"
          paginationModel={{ page, pageSize: PAGE_SIZE }}
          onPaginationModelChange={({ page: p }) => setPage(p)}
          pageSizeOptions={[PAGE_SIZE]}
          onRowClick={({ row }) => navigate(`/invoices/${row.id}`)}
          autoHeight
          disableRowSelectionOnClick
          sx={{
            bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
            '& .MuiDataGrid-row': { cursor: 'pointer' },
          }}
        />
      )}
    </Box>
  )
}
