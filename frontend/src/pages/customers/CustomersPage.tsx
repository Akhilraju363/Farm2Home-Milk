import {
  Box, TextField, MenuItem, Chip, InputAdornment, IconButton, Tooltip, Button,
  Menu, ListItemIcon, ListItemText, Typography, Paper, Pagination, Alert, CircularProgress, Avatar,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import {
  Search, Refresh, Add, Close, Download, People, MoreVert, Visibility, Edit,
  ToggleOff, ToggleOn, Block,
} from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { AddCustomerDialog } from '../../components/customer/AddCustomerDialog'
import { EditCustomerDialog } from '../../components/customer/EditCustomerDialog'
import { useDebounced } from '../../hooks/useDebounced'
import { customerService } from '../../services/customerService'
import { downloadBlob } from '../../utils/download'
import { formatDate } from '../../utils/formatters'
import { CUSTOMER_STATUS_LABELS } from '../../types/customer.types'
import type { Customer, CustomerStatus } from '../../types/customer.types'

const PAGE_SIZE = 20
const DEBOUNCE_MS = 400

const STATUS_COLOR: Record<CustomerStatus, 'success' | 'default' | 'error'> = {
  ACTIVE: 'success', INACTIVE: 'default', SUSPENDED: 'error',
}

export function CustomersPage() {
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [exporting, setExporting] = useState(false)

  const [addOpen, setAddOpen] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null)
  const [statusTarget, setStatusTarget] = useState<{ customer: Customer; next: CustomerStatus } | null>(null)
  const [rowMenu, setRowMenu] = useState<{ anchor: HTMLElement; customer: Customer } | null>(null)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const hasActiveFilters = Boolean(debouncedSearch || statusFilter || dateFrom || dateTo)

  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    status: (statusFilter || undefined) as CustomerStatus | undefined,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page, size: PAGE_SIZE,
  }), [debouncedSearch, statusFilter, dateFrom, dateTo, page])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['customers', 'search', searchParams],
    queryFn: () => customerService.searchCustomers(searchParams),
  })

  const customers = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const statusMutation = useMutation({
    mutationFn: ({ customer, next }: { customer: Customer; next: CustomerStatus }) =>
      customerService.update(customer.id, { status: next }),
    onSuccess: (_res, { next }) => {
      enqueueSnackbar(`Customer marked ${CUSTOMER_STATUS_LABELS[next].toLowerCase()}`, { variant: 'success' })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      setStatusTarget(null)
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? 'Could not update the customer. Please try again.', { variant: 'error' })
      setStatusTarget(null)
    },
  })

  const resetFilters = () => { setSearch(''); setStatusFilter(''); setDateFrom(''); setDateTo(''); setPage(0) }

  const handleExport = async () => {
    setExporting(true)
    try {
      const { blob, filename } = await customerService.export({ ...searchParams, format: 'CSV' })
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar("Couldn't export customers. Please try again.", { variant: 'error' })
    } finally {
      setExporting(false)
    }
  }

  const handleSaved = () => {
    setAddOpen(false)
    setEditingCustomer(null)
    queryClient.invalidateQueries({ queryKey: ['customers'] })
  }

  const columns: GridColDef<Customer>[] = [
    {
      field: 'firstName', headerName: 'Customer', flex: 1.4, minWidth: 220,
      renderCell: ({ row }) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, cursor: 'pointer', py: 0.5 }}
          onClick={() => navigate(`/customers/${row.id}`)}>
          <Avatar src={row.profileImageUrl ?? undefined} sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 13 }}>
            {row.firstName?.[0]}{row.lastName?.[0]}
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" fontWeight={600} noWrap>{row.firstName} {row.lastName}</Typography>
            <Typography variant="caption" color="text.secondary">{row.customerCode}</Typography>
          </Box>
        </Box>
      ),
    },
    { field: 'mobile', headerName: 'Mobile', width: 130 },
    {
      field: 'email', headerName: 'Email', flex: 1, minWidth: 170,
      renderCell: ({ value }) => value || <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    {
      field: 'createdAt', headerName: 'Join Date', width: 130,
      renderCell: ({ value }) => formatDate(value as string),
    },
    {
      field: 'status', headerName: 'Status', width: 120,
      renderCell: ({ value }) => (
        <Chip label={CUSTOMER_STATUS_LABELS[value as CustomerStatus]} size="small" color={STATUS_COLOR[value as CustomerStatus]} sx={{ fontWeight: 600 }} />
      ),
    },
    {
      field: 'actions', headerName: '', width: 60, sortable: false, filterable: false,
      renderCell: ({ row }) => (
        <IconButton size="small" onClick={(e) => setRowMenu({ anchor: e.currentTarget, customer: row })}>
          <MoreVert fontSize="small" />
        </IconButton>
      ),
    },
  ]

  return (
    <Box>
      <PageHeader
        title="Customer Management"
        subtitle="Manage your Farm2Home customer base and customer accounts."
        action={{ label: 'Add Customer', icon: <Add />, onClick: () => setAddOpen(true) }}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search name, mobile, email, customer ID…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          sx={{ minWidth: 280, flex: 1 }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
            endAdornment: search && (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearch('')}><Close fontSize="small" /></IconButton>
              </InputAdornment>
            ),
          }}
        />
        <TextField select size="small" label="Status" value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }} sx={{ minWidth: 140 }}>
          <MenuItem value="">All Status</MenuItem>
          <MenuItem value="ACTIVE">Active</MenuItem>
          <MenuItem value="INACTIVE">Inactive</MenuItem>
          <MenuItem value="SUSPENDED">Suspended</MenuItem>
        </TextField>
        <TextField
          size="small" label="Joined From" type="date" value={dateFrom}
          onChange={(e) => { setDateFrom(e.target.value); setPage(0) }}
          InputLabelProps={{ shrink: true }} sx={{ minWidth: 160 }}
        />
        <TextField
          size="small" label="Joined To" type="date" value={dateTo}
          onChange={(e) => { setDateTo(e.target.value); setPage(0) }}
          InputLabelProps={{ shrink: true }} sx={{ minWidth: 160 }}
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
          Couldn't load customers. Please check your connection and try again.
        </Alert>
      ) : !isLoading && customers.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <People sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No customers match your search.' : 'No customers found.'}
          </Typography>
          {hasActiveFilters && <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>}
        </Paper>
      ) : (
        <DataGrid
          rows={customers}
          columns={columns}
          loading={isLoading || isFetching}
          rowHeight={56}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
        />
      )}

      {!isLoading && !isError && totalElements > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} customer{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}

      <Menu anchorEl={rowMenu?.anchor} open={Boolean(rowMenu)} onClose={() => setRowMenu(null)}>
        <MenuItem onClick={() => { navigate(`/customers/${rowMenu?.customer.id}`); setRowMenu(null) }}>
          <ListItemIcon><Visibility fontSize="small" /></ListItemIcon>
          <ListItemText>View</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { if (rowMenu) setEditingCustomer(rowMenu.customer); setRowMenu(null) }}>
          <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
          <ListItemText>Edit</ListItemText>
        </MenuItem>
        {rowMenu?.customer.status !== 'ACTIVE' && (
          <MenuItem onClick={() => { if (rowMenu) setStatusTarget({ customer: rowMenu.customer, next: 'ACTIVE' }); setRowMenu(null) }}>
            <ListItemIcon><ToggleOn fontSize="small" /></ListItemIcon>
            <ListItemText>Activate</ListItemText>
          </MenuItem>
        )}
        {rowMenu?.customer.status !== 'SUSPENDED' && (
          <MenuItem onClick={() => { if (rowMenu) setStatusTarget({ customer: rowMenu.customer, next: 'SUSPENDED' }); setRowMenu(null) }}>
            <ListItemIcon><Block fontSize="small" /></ListItemIcon>
            <ListItemText>Suspend</ListItemText>
          </MenuItem>
        )}
        {rowMenu?.customer.status !== 'INACTIVE' && (
          <MenuItem onClick={() => { if (rowMenu) setStatusTarget({ customer: rowMenu.customer, next: 'INACTIVE' }); setRowMenu(null) }}>
            <ListItemIcon><ToggleOff fontSize="small" /></ListItemIcon>
            <ListItemText>Deactivate</ListItemText>
          </MenuItem>
        )}
      </Menu>

      <AddCustomerDialog open={addOpen} onClose={() => setAddOpen(false)} onSaved={handleSaved} />
      <EditCustomerDialog open={Boolean(editingCustomer)} customer={editingCustomer} onClose={() => setEditingCustomer(null)} onSaved={handleSaved} />

      <ConfirmDialog
        open={Boolean(statusTarget)}
        title={`${statusTarget?.next === 'ACTIVE' ? 'Activate' : statusTarget?.next === 'SUSPENDED' ? 'Suspend' : 'Deactivate'} Customer?`}
        message={`"${statusTarget?.customer.firstName} ${statusTarget?.customer.lastName}" will be marked ${statusTarget ? CUSTOMER_STATUS_LABELS[statusTarget.next].toLowerCase() : ''}.`}
        confirmLabel={statusTarget?.next === 'ACTIVE' ? 'Activate' : statusTarget?.next === 'SUSPENDED' ? 'Suspend' : 'Deactivate'}
        destructive={statusTarget?.next !== 'ACTIVE'}
        loading={statusMutation.isPending}
        onConfirm={() => statusTarget && statusMutation.mutate(statusTarget)}
        onClose={() => setStatusTarget(null)}
      />
    </Box>
  )
}
