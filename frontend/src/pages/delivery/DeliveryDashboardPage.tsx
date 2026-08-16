import {
  Box, TextField, InputAdornment, IconButton, Tooltip, Button, Typography, Paper,
  Pagination, Alert, Chip, Tabs, Tab, Grid, Card,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import {
  Search, Refresh, Add, Close, LocalShipping, CheckCircleOutline, SmartToy, Person,
  HourglassEmpty,
} from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { AssignDeliveryDialog } from '../../components/delivery/AssignDeliveryDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { deliveryService } from '../../services/deliveryService'
import { orderService } from '../../services/orderService'
import { formatDateTime, statusColor } from '../../utils/formatters'
import { ASSIGNMENT_STATUS_LABELS } from '../../types/delivery.types'
import type { Assignment, AssignmentStatus } from '../../types/delivery.types'

const PAGE_SIZE = 20
const DEBOUNCE_MS = 400

const STATUS_TABS: Array<{ value: AssignmentStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'FAILED', label: 'Failed' },
]

function KpiCard({ label, value, icon }: { label: string; value: number | string; icon: React.ReactNode }) {
  return (
    <Card variant="outlined" sx={{ p: 2, borderRadius: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
      <Box sx={{ color: 'primary.main' }}>{icon}</Box>
      <Box>
        <Typography variant="h6" fontWeight={700}>{value}</Typography>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
      </Box>
    </Card>
  )
}

export function DeliveryDashboardPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  // FARM_MANAGER/SUPER_ADMIN only - matches DeliveryAssignmentController.manualAssign's
  // @PreAuthorize exactly. Narrower than "can view the dashboard" (which now also includes
  // DELIVERY_MANAGER, per delivery-service's broadened isAdmin()) - a DELIVERY_MANAGER can see
  // every delivery here but cannot create a new assignment (no partner-list access either).
  const canAssign = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<AssignmentStatus | ''>('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [assignOpen, setAssignOpen] = useState(false)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const hasActiveFilters = Boolean(debouncedSearch || status || dateFrom || dateTo)

  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    status: (status || undefined) as AssignmentStatus | undefined,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page, size: PAGE_SIZE,
  }), [debouncedSearch, status, dateFrom, dateTo, page])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['delivery', 'search', searchParams],
    queryFn: () => deliveryService.search(searchParams),
  })

  const assignments = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  // Real, server-reported totalElements per status - not a client-side count of the current page.
  const { data: summaryData } = useQuery({
    queryKey: ['delivery', 'summary'],
    queryFn: () => deliveryService.getSummary(),
  })
  const { data: assignedCountData } = useQuery({
    queryKey: ['delivery', 'count', 'ASSIGNED'],
    queryFn: () => deliveryService.search({ status: 'ASSIGNED', size: 1 }),
  })
  const { data: outForDeliveryCountData } = useQuery({
    queryKey: ['delivery', 'count', 'OUT_FOR_DELIVERY'],
    queryFn: () => deliveryService.search({ status: 'OUT_FOR_DELIVERY', size: 1 }),
  })

  // Orders waiting for assignment: order-service's own pendingOrders count (an Order only leaves
  // PENDING once a DeliveryAssignment - auto or manual - exists, see order-service's
  // DeliveryEventConsumer) - reused as-is, not re-derived from delivery-service's own data.
  const { data: orderSummaryData } = useQuery({
    queryKey: ['orders', 'summary'],
    queryFn: () => orderService.getSummary(),
  })
  const { data: autoAssignedCountData } = useQuery({
    queryKey: ['delivery', 'count', 'auto-assigned'],
    queryFn: () => deliveryService.search({ autoAssigned: true, size: 1 }),
  })
  const { data: manualAssignedCountData } = useQuery({
    queryKey: ['delivery', 'count', 'manual-assigned'],
    queryFn: () => deliveryService.search({ autoAssigned: false, size: 1 }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['delivery'] })
  const resetFilters = () => { setSearch(''); setStatus(''); setDateFrom(''); setDateTo(''); setPage(0) }
  const handleAssigned = () => { setAssignOpen(false); invalidate() }

  const columns: GridColDef<Assignment>[] = [
    {
      field: 'orderId', headerName: 'Order', width: 160,
      renderCell: ({ value }) => (
        <Typography
          variant="caption" sx={{ fontFamily: 'monospace', color: 'primary.main', cursor: 'pointer' }}
          onClick={(e) => { e.stopPropagation(); navigate(`/orders/${value}`) }}
        >
          {String(value).slice(0, 8)}…
        </Typography>
      ),
    },
    { field: 'deliveryPartnerName', headerName: 'Delivery Partner', width: 160 },
    { field: 'deliveryPartnerMobile', headerName: 'Mobile', width: 130 },
    { field: 'routeCode', headerName: 'Route', width: 100 },
    {
      field: 'autoAssigned', headerName: 'Assigned By', width: 130,
      renderCell: ({ value }) => (
        <Chip
          size="small" variant="outlined"
          icon={value ? <SmartToy sx={{ fontSize: 14 }} /> : <Person sx={{ fontSize: 14 }} />}
          label={value ? 'Automatic' : 'Manual'}
        />
      ),
    },
    {
      field: 'status', headerName: 'Status', width: 150,
      renderCell: ({ value }) => <Chip label={ASSIGNMENT_STATUS_LABELS[value as AssignmentStatus]} size="small" color={statusColor(value as string)} sx={{ fontWeight: 600 }} />,
    },
    { field: 'assignedAt', headerName: 'Assigned At', width: 170, renderCell: ({ value }) => formatDateTime(value as string) },
  ]

  return (
    <Box>
      <PageHeader
        title="Delivery Management"
        subtitle="Track and manage Farm2Home delivery assignments."
        action={canAssign ? { label: 'Assign Delivery', icon: <Add />, onClick: () => setAssignOpen(true) } : undefined}
      />

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <Chip
          size="small" color="success" variant="outlined" icon={<SmartToy sx={{ fontSize: 14 }} />}
          label="Automatic Assignment: Enabled"
        />
        <Tooltip title="New orders are automatically assigned to the least-loaded active delivery partner on their route. If none is available, the order stays unassigned here for manual assignment.">
          <Typography variant="caption" color="text.secondary" sx={{ cursor: 'help' }}>What does this mean?</Typography>
        </Tooltip>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Delivered Today" value={summaryData?.data.data.completedDeliveriesToday ?? '—'} icon={<CheckCircleOutline />} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Assigned" value={assignedCountData?.data.data.totalElements ?? '—'} icon={<LocalShipping />} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Out for Delivery" value={outForDeliveryCountData?.data.data.totalElements ?? '—'} icon={<LocalShipping />} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="This View" value={totalElements} icon={<LocalShipping />} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Orders Waiting for Assignment" value={orderSummaryData?.data.data.pendingOrders ?? '—'} icon={<HourglassEmpty />} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Automatically Assigned" value={autoAssignedCountData?.data.data.totalElements ?? '—'} icon={<SmartToy />} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Manually Assigned" value={manualAssignedCountData?.data.data.totalElements ?? '—'} icon={<Person />} />
        </Grid>
      </Grid>

      <Tabs
        value={status} onChange={(_e, v) => { setStatus(v); setPage(0) }}
        sx={{ mb: 2, borderBottom: '1px solid', borderColor: 'divider' }}
      >
        {STATUS_TABS.map((t) => <Tab key={t.value} value={t.value} label={t.label} />)}
      </Tabs>

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search partner name, route…"
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
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Couldn't load deliveries. Please check your connection and try again.
        </Alert>
      ) : !isLoading && assignments.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <LocalShipping sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No deliveries match your search.' : 'No deliveries found.'}
          </Typography>
          {hasActiveFilters ? (
            <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>
          ) : canAssign ? (
            <Button variant="contained" startIcon={<Add />} onClick={() => setAssignOpen(true)} sx={{ mt: 1 }}>Assign Delivery</Button>
          ) : null}
        </Paper>
      ) : (
        <DataGrid
          rows={assignments}
          columns={columns}
          loading={isLoading || isFetching}
          rowHeight={56}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          onRowClick={(params) => navigate(`/delivery/${params.id}`)}
          sx={{
            bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
            '& .MuiDataGrid-row': { cursor: 'pointer' },
          }}
        />
      )}

      {!isLoading && !isError && totalElements > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} deliver{totalElements === 1 ? 'y' : 'ies'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}

      {canAssign && (
        <AssignDeliveryDialog open={assignOpen} onClose={() => setAssignOpen(false)} onAssigned={handleAssigned} />
      )}
    </Box>
  )
}
