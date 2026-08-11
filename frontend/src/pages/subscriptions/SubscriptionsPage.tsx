import {
  Box, TextField, MenuItem, InputAdornment, IconButton, Tooltip, Button, Menu, ListItemIcon,
  ListItemText, Typography, Paper, Pagination, Alert, CircularProgress, Chip, Tabs, Tab,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import {
  Search, Refresh, Add, Close, Download, Subscriptions as SubscriptionsIcon, MoreVert,
  Visibility, Edit, PauseCircle, PlayCircle, Cancel as CancelIcon,
} from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { SubscriptionFormDialog } from '../../components/subscription/SubscriptionFormDialog'
import { PauseSubscriptionDialog } from '../../components/subscription/PauseSubscriptionDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { subscriptionService } from '../../services/subscriptionService'
import { downloadBlob } from '../../utils/download'
import { formatDate, statusColor } from '../../utils/formatters'
import { MILK_TYPE_LABELS, MILK_TYPES, SUBSCRIPTION_STATUS_LABELS } from '../../types/subscription.types'
import type { Subscription, SubscriptionStatus } from '../../types/subscription.types'

const PAGE_SIZE = 20
const DEBOUNCE_MS = 400

const STATUS_TABS: Array<{ value: SubscriptionStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PAUSED', label: 'Paused' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'EXPIRED', label: 'Expired' },
]

export function SubscriptionsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  // Matches subscription-service's own UserPrincipal.isAdmin() exactly (FARM_MANAGER +
  // SUPER_ADMIN) - notably NOT DELIVERY_MANAGER, unlike Product/Customer Management's gates.
  const canManage = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<SubscriptionStatus | ''>('')
  const [milkTypeFilter, setMilkTypeFilter] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [exporting, setExporting] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [editingSubscription, setEditingSubscription] = useState<Subscription | null>(null)
  const [pauseTarget, setPauseTarget] = useState<string | null>(null)
  const [resumeTarget, setResumeTarget] = useState<Subscription | null>(null)
  const [cancelTarget, setCancelTarget] = useState<Subscription | null>(null)
  const [rowMenu, setRowMenu] = useState<{ anchor: HTMLElement; subscription: Subscription } | null>(null)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const hasActiveFilters = Boolean(debouncedSearch || status || milkTypeFilter || dateFrom || dateTo)

  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    status: (status || undefined) as SubscriptionStatus | undefined,
    milkType: (milkTypeFilter || undefined) as any,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page, size: PAGE_SIZE,
  }), [debouncedSearch, status, milkTypeFilter, dateFrom, dateTo, page])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['subscriptions', 'search', searchParams],
    queryFn: () => subscriptionService.search(searchParams),
  })

  const subscriptions = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['subscriptions'] })

  const resumeMutation = useMutation({
    mutationFn: (id: string) => subscriptionService.resume(id),
    onSuccess: () => { enqueueSnackbar('Subscription resumed successfully', { variant: 'success' }); invalidate(); setResumeTarget(null) },
    onError: (err: any) => { enqueueSnackbar(err.response?.data?.message ?? 'Could not resume the subscription.', { variant: 'error' }); setResumeTarget(null) },
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => subscriptionService.cancel(id),
    onSuccess: () => { enqueueSnackbar('Subscription cancelled successfully', { variant: 'success' }); invalidate(); setCancelTarget(null) },
    onError: (err: any) => { enqueueSnackbar(err.response?.data?.message ?? 'Could not cancel the subscription.', { variant: 'error' }); setCancelTarget(null) },
  })

  const resetFilters = () => { setSearch(''); setStatus(''); setMilkTypeFilter(''); setDateFrom(''); setDateTo(''); setPage(0) }

  const handleExport = async () => {
    setExporting(true)
    try {
      const { blob, filename } = await subscriptionService.export({ ...searchParams, format: 'CSV' })
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar("Couldn't export subscriptions. Please try again.", { variant: 'error' })
    } finally {
      setExporting(false)
    }
  }

  const openAdd = () => { setEditingSubscription(null); setFormOpen(true) }
  const openEdit = (s: Subscription) => { setEditingSubscription(s); setFormOpen(true) }
  const handleSaved = () => { setFormOpen(false); invalidate() }
  const handlePauseSaved = () => { setPauseTarget(null); invalidate() }

  const columns: GridColDef<Subscription>[] = [
    ...(canManage ? [{
      field: 'customerId', headerName: 'Customer', width: 140,
      renderCell: ({ value }: { value: string }) => (
        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{value.slice(0, 8)}…</Typography>
      ),
    } as GridColDef<Subscription>] : []),
    {
      field: 'milkType', headerName: 'Milk Type', width: 130,
      renderCell: ({ row }) => (
        <Box sx={{ cursor: 'pointer' }} onClick={() => navigate(`/subscriptions/${row.id}`)}>
          {MILK_TYPE_LABELS[row.milkType]}
        </Box>
      ),
    },
    {
      field: 'quantity', headerName: 'Quantity', width: 100,
      renderCell: ({ value }) => `${value} L`,
    },
    {
      field: 'scheduleType', headerName: 'Frequency', width: 130,
      renderCell: ({ value }) => value === 'ALTERNATE_DAY' ? 'Alternate Day' : value === 'DAILY' ? 'Daily' : 'Weekly',
    },
    { field: 'startDate', headerName: 'Start Date', width: 120, renderCell: ({ value }) => formatDate(value as string) },
    { field: 'endDate', headerName: 'End Date', width: 120, renderCell: ({ value }) => formatDate(value as string | undefined) },
    {
      field: 'status', headerName: 'Status', width: 120,
      renderCell: ({ value }) => <Chip label={SUBSCRIPTION_STATUS_LABELS[value as SubscriptionStatus]} size="small" color={statusColor(value as string)} sx={{ fontWeight: 600 }} />,
    },
    {
      field: 'actions', headerName: '', width: 60, sortable: false, filterable: false,
      renderCell: ({ row }) => (
        <IconButton size="small" onClick={(e) => setRowMenu({ anchor: e.currentTarget, subscription: row })}>
          <MoreVert fontSize="small" />
        </IconButton>
      ),
    },
  ]

  return (
    <Box>
      <PageHeader
        title="Subscription Management"
        subtitle="Manage recurring Farm2Home milk deliveries and customer subscriptions."
        action={{ label: 'New Subscription', icon: <Add />, onClick: openAdd }}
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
          placeholder="Search milk type, schedule…"
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
          size="small" label="Start From" type="date" value={dateFrom}
          onChange={(e) => { setDateFrom(e.target.value); setPage(0) }}
          InputLabelProps={{ shrink: true }} sx={{ minWidth: 150 }}
        />
        <TextField
          size="small" label="Start To" type="date" value={dateTo}
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
          Couldn't load subscriptions. Please check your connection and try again.
        </Alert>
      ) : !isLoading && subscriptions.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <SubscriptionsIcon sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters
              ? 'No subscriptions match your search.'
              : status === 'ACTIVE' ? 'No active subscriptions.'
              : status === 'PAUSED' ? 'No paused subscriptions.'
              : status === 'CANCELLED' ? 'No cancelled subscriptions.'
              : status === 'EXPIRED' ? 'No expired subscriptions.'
              : 'No subscriptions found.'}
          </Typography>
          {hasActiveFilters ? (
            <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>
          ) : (
            <Button variant="contained" startIcon={<Add />} onClick={openAdd} sx={{ mt: 1 }}>New Subscription</Button>
          )}
        </Paper>
      ) : (
        <DataGrid
          rows={subscriptions}
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
            {totalElements.toLocaleString()} subscription{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}

      <Menu anchorEl={rowMenu?.anchor} open={Boolean(rowMenu)} onClose={() => setRowMenu(null)}>
        <MenuItem onClick={() => { navigate(`/subscriptions/${rowMenu?.subscription.id}`); setRowMenu(null) }}>
          <ListItemIcon><Visibility fontSize="small" /></ListItemIcon>
          <ListItemText>View</ListItemText>
        </MenuItem>
        {rowMenu && rowMenu.subscription.status !== 'CANCELLED' && rowMenu.subscription.status !== 'EXPIRED' && (
          <MenuItem onClick={() => { openEdit(rowMenu.subscription); setRowMenu(null) }}>
            <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
            <ListItemText>Edit</ListItemText>
          </MenuItem>
        )}
        {rowMenu?.subscription.status === 'ACTIVE' && (
          <MenuItem onClick={() => { setPauseTarget(rowMenu.subscription.id); setRowMenu(null) }}>
            <ListItemIcon><PauseCircle fontSize="small" /></ListItemIcon>
            <ListItemText>Pause</ListItemText>
          </MenuItem>
        )}
        {rowMenu?.subscription.status === 'PAUSED' && (
          <MenuItem onClick={() => { setResumeTarget(rowMenu.subscription); setRowMenu(null) }}>
            <ListItemIcon><PlayCircle fontSize="small" /></ListItemIcon>
            <ListItemText>Resume</ListItemText>
          </MenuItem>
        )}
        {rowMenu && rowMenu.subscription.status !== 'CANCELLED' && rowMenu.subscription.status !== 'EXPIRED' && (
          <MenuItem onClick={() => { setCancelTarget(rowMenu.subscription); setRowMenu(null) }} sx={{ color: 'error.main' }}>
            <ListItemIcon><CancelIcon fontSize="small" color="error" /></ListItemIcon>
            <ListItemText>Cancel</ListItemText>
          </MenuItem>
        )}
      </Menu>

      <SubscriptionFormDialog
        open={formOpen} subscription={editingSubscription} canSelectCustomer={canManage}
        onClose={() => setFormOpen(false)} onSaved={handleSaved}
      />

      <PauseSubscriptionDialog
        open={Boolean(pauseTarget)} subscriptionId={pauseTarget}
        onClose={() => setPauseTarget(null)} onSaved={handlePauseSaved}
      />

      <ConfirmDialog
        open={Boolean(resumeTarget)}
        title="Resume Subscription?"
        message="Deliveries will restart according to the original schedule."
        confirmLabel="Resume"
        loading={resumeMutation.isPending}
        onConfirm={() => resumeTarget && resumeMutation.mutate(resumeTarget.id)}
        onClose={() => setResumeTarget(null)}
      />

      <ConfirmDialog
        open={Boolean(cancelTarget)}
        title="Cancel Subscription?"
        message="This is permanent - a cancelled subscription cannot be resumed. The customer will need to create a new one."
        confirmLabel="Cancel Subscription"
        destructive
        loading={cancelMutation.isPending}
        onConfirm={() => cancelTarget && cancelMutation.mutate(cancelTarget.id)}
        onClose={() => setCancelTarget(null)}
      />
    </Box>
  )
}
