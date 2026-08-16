import {
  Box, Button, IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Tooltip, Paper,
  Typography, TextField, InputAdornment, Chip,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import {
  Add, Refresh, MoreVert, Edit, Delete, AltRoute, Search, Close, ToggleOn, ToggleOff,
} from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { RouteFormDialog } from '../../components/delivery/RouteFormDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { deliveryRouteService } from '../../services/deliveryService'
import { formatDate } from '../../utils/formatters'
import type { DeliveryRoute } from '../../types/delivery.types'

const PAGE_SIZE = 20
const DEBOUNCE_MS = 350

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'true', label: 'Active' },
  { value: 'false', label: 'Inactive' },
]

export function RouteManagementPage() {
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  // Route management is FARM_MANAGER/SUPER_ADMIN only on the backend (DeliveryRouteController) -
  // narrower than DELIVERY_MANAGER's general delivery-ops access, matching DeliveryPartner
  // management and manualAssign, which are also FARM_MANAGER/SUPER_ADMIN only.
  const canWrite = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [editingRoute, setEditingRoute] = useState<DeliveryRoute | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<DeliveryRoute | null>(null)
  const [rowMenu, setRowMenu] = useState<{ anchor: HTMLElement; route: DeliveryRoute } | null>(null)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    active: statusFilter === '' ? undefined : statusFilter === 'true',
    page, size: PAGE_SIZE,
  }), [debouncedSearch, statusFilter, page])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['delivery', 'routes', 'search', searchParams],
    queryFn: () => deliveryRouteService.search(searchParams),
  })
  const routes = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['delivery', 'routes'] })

  const statusMutation = useMutation({
    mutationFn: (r: DeliveryRoute) => deliveryRouteService.updateStatus(r.id, { active: !r.active }),
    onSuccess: (_res, r) => {
      enqueueSnackbar(r.active ? 'Route deactivated' : 'Route activated', { variant: 'success' })
      invalidate()
    },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? "Couldn't update the route status.", { variant: 'error' }),
  })

  const deleteMutation = useMutation({
    mutationFn: (r: DeliveryRoute) => deliveryRouteService.delete(r.id),
    onSuccess: () => {
      enqueueSnackbar('Route deleted successfully', { variant: 'success' })
      invalidate()
      setDeleteTarget(null)
    },
    onError: (err: any) => {
      // 409 when the route is referenced by an active assignment - the real server message is
      // shown as-is rather than a generic fallback, since it tells the admin exactly why (and
      // that deactivating instead is the available alternative).
      enqueueSnackbar(err.response?.data?.message ?? "Couldn't delete this route. Please try again.", { variant: 'error' })
      setDeleteTarget(null)
    },
  })

  const openAdd = () => { setEditingRoute(null); setFormOpen(true) }
  const openEdit = (r: DeliveryRoute) => { setEditingRoute(r); setFormOpen(true) }
  const handleSaved = () => { setFormOpen(false); invalidate() }

  const columns: GridColDef<DeliveryRoute>[] = [
    { field: 'routeCode', headerName: 'Route Code', width: 130 },
    { field: 'routeName', headerName: 'Route Name', flex: 1, minWidth: 180 },
    // Every route covers the same delivery zone (Farm2Home's own 10 KM radius, not a distinct
    // city per route - see DeliveryRouteSelectionServiceImpl), so this shows only `area`
    // ("Within 10 km of Farm2Home"), never a per-route city string. Farm2Home's real address
    // lives in exactly one place - farm-service's BusinessSettings, shown on Settings -> Farm &
    // Business - and is never duplicated here.
    {
      field: 'area', headerName: 'Area', flex: 1, minWidth: 220,
      renderCell: ({ row }) => row.area,
    },
    { field: 'pincode', headerName: 'Pincode', width: 110 },
    {
      field: 'active', headerName: 'Status', width: 110,
      renderCell: ({ value }) => <Chip label={value ? 'Active' : 'Inactive'} size="small" color={value ? 'success' : 'default'} />,
    },
    {
      field: 'createdAt', headerName: 'Created Date', width: 140,
      renderCell: ({ value }) => formatDate(value as string),
    },
    {
      field: 'actions', headerName: '', width: 60, sortable: false, filterable: false,
      renderCell: ({ row }) => canWrite ? (
        <IconButton size="small" onClick={(e) => setRowMenu({ anchor: e.currentTarget, route: row })}>
          <MoreVert fontSize="small" />
        </IconButton>
      ) : null,
    },
  ]

  return (
    <Box>
      <PageHeader
        title="Route Management"
        subtitle="Manage delivery routes used when assigning orders to delivery partners."
        action={canWrite ? { label: 'Create Route', icon: <Add />, onClick: openAdd } : undefined}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          size="small"
          placeholder="Search route name, code, area, city, pincode…"
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
        <TextField
          select size="small" label="Status" value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }} sx={{ minWidth: 160 }}
        >
          {STATUS_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
        </TextField>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {isError ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <Typography variant="h6" fontWeight={700} gutterBottom>Couldn't load routes.</Typography>
          <Button variant="outlined" onClick={() => refetch()} sx={{ mt: 1 }}>Retry</Button>
        </Paper>
      ) : !isLoading && routes.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <AltRoute sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {debouncedSearch || statusFilter ? 'No routes match your search.' : 'No delivery routes have been created yet.'}
          </Typography>
          {canWrite && !debouncedSearch && !statusFilter && (
            <Button variant="contained" startIcon={<Add />} onClick={openAdd} sx={{ mt: 1 }}>Create Route</Button>
          )}
        </Paper>
      ) : (
        <DataGrid
          rows={routes}
          columns={columns}
          loading={isLoading || isFetching}
          rowCount={totalElements}
          paginationMode="server"
          paginationModel={{ page, pageSize: PAGE_SIZE }}
          onPaginationModelChange={({ page: p }) => setPage(p)}
          pageSizeOptions={[PAGE_SIZE]}
          getRowHeight={() => 56}
          autoHeight
          disableRowSelectionOnClick
          sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
        />
      )}

      <Menu anchorEl={rowMenu?.anchor} open={Boolean(rowMenu)} onClose={() => setRowMenu(null)}>
        <MenuItem onClick={() => { if (rowMenu) openEdit(rowMenu.route); setRowMenu(null) }}>
          <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
          <ListItemText>Edit</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { if (rowMenu) statusMutation.mutate(rowMenu.route); setRowMenu(null) }}>
          <ListItemIcon>{rowMenu?.route.active ? <ToggleOff fontSize="small" /> : <ToggleOn fontSize="small" />}</ListItemIcon>
          <ListItemText>{rowMenu?.route.active ? 'Deactivate' : 'Activate'}</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { if (rowMenu) setDeleteTarget(rowMenu.route); setRowMenu(null) }} sx={{ color: 'error.main' }}>
          <ListItemIcon><Delete fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Delete</ListItemText>
        </MenuItem>
      </Menu>

      <RouteFormDialog open={formOpen} route={editingRoute} onClose={() => setFormOpen(false)} onSaved={handleSaved} />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete Route?"
        message={`"${deleteTarget?.routeName}" (${deleteTarget?.routeCode}) will be removed. This cannot be undone. If it's currently assigned to an active delivery, deletion will be rejected - deactivate it instead.`}
        confirmLabel="Delete"
        destructive
        loading={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
