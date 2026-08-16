import {
  Box, Button, IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Tooltip, Paper,
  Typography, TextField, InputAdornment, Avatar, CircularProgress, Alert,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { Add, Refresh, MoreVert, Edit, Delete, Agriculture, Search, Close, MyLocation, Check } from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { FarmFormDialog } from '../../components/settings/FarmFormDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { farmService } from '../../services/farmService'
import { formatDate } from '../../utils/formatters'
import type { Farm } from '../../types/farm.types'

function BusinessSettingsCard({ canWrite }: { canWrite: boolean }) {
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [radiusInput, setRadiusInput] = useState('')
  const [latInput, setLatInput] = useState('')
  const [lonInput, setLonInput] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['farm', 'business-settings'],
    queryFn: () => farmService.getBusinessSettings(),
  })
  const settings = data?.data.data

  const startEdit = () => {
    if (!settings) return
    setRadiusInput(String(settings.deliveryRadiusKm))
    setLatInput(String(settings.farmLatitude))
    setLonInput(String(settings.farmLongitude))
    setEditing(true)
  }

  const mutation = useMutation({
    mutationFn: () => farmService.updateBusinessSettings({
      farmLatitude: Number(latInput), farmLongitude: Number(lonInput), deliveryRadiusKm: Number(radiusInput),
    }),
    onSuccess: () => {
      enqueueSnackbar('Delivery settings updated', { variant: 'success' })
      queryClient.invalidateQueries({ queryKey: ['farm', 'business-settings'] })
      setEditing(false)
    },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? "Couldn't update delivery settings.", { variant: 'error' }),
  })

  return (
    <Paper variant="outlined" sx={{ p: 2.5, mb: 3, borderRadius: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <MyLocation color="primary" fontSize="small" />
          <Typography variant="subtitle1" fontWeight={700}>Delivery Coverage</Typography>
        </Box>
        {canWrite && !editing && settings && (
          <Button size="small" startIcon={<Edit fontSize="small" />} onClick={startEdit}>Edit</Button>
        )}
      </Box>

      {isLoading ? (
        <CircularProgress size={20} />
      ) : isError ? (
        <Alert severity="error">Couldn't load delivery settings.</Alert>
      ) : editing ? (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          <TextField label="Latitude" size="small" type="number" value={latInput}
            onChange={(e) => setLatInput(e.target.value)} sx={{ width: 160 }} />
          <TextField label="Longitude" size="small" type="number" value={lonInput}
            onChange={(e) => setLonInput(e.target.value)} sx={{ width: 160 }} />
          <TextField label="Delivery Radius (KM)" size="small" type="number" value={radiusInput}
            onChange={(e) => setRadiusInput(e.target.value)} sx={{ width: 170 }} />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              variant="contained" size="small" startIcon={<Check fontSize="small" />}
              disabled={mutation.isPending} onClick={() => mutation.mutate()}
            >
              Save
            </Button>
            <Button size="small" color="inherit" disabled={mutation.isPending} onClick={() => setEditing(false)}>Cancel</Button>
          </Box>
        </Box>
      ) : settings ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* Farm2Home's real business address - sourced from BusinessSettings alone (the one
              authoritative place it's stored anywhere in this app), not editable here yet (the
              spec for this fix asked only that it be displayed correctly - coordinates/radius
              already had an edit flow before this address existed). */}
          {(settings.addressLine || settings.locality || settings.city) && (
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">Address</Typography>
              <Typography variant="body1" fontWeight={600}>
                {[settings.addressLine, settings.locality, settings.city].filter(Boolean).join(', ')}
                {settings.district ? `, ${settings.district}` : ''}
                {settings.state ? `, ${settings.state}` : ''}
                {settings.pincode ? ` - ${settings.pincode}` : ''}
              </Typography>
            </Box>
          )}
          <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">Delivery Radius</Typography>
              <Typography variant="body1" fontWeight={600}>{settings.deliveryRadiusKm} KM</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">Farm Location (Latitude)</Typography>
              <Typography variant="body1" fontWeight={600}>{settings.farmLatitude}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">Farm Location (Longitude)</Typography>
              <Typography variant="body1" fontWeight={600}>{settings.farmLongitude}</Typography>
            </Box>
          </Box>
        </Box>
      ) : null}
    </Paper>
  )
}

const PAGE_SIZE = 20
const DEBOUNCE_MS = 350

export function FarmsPage() {
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  // Only FARM_MANAGER/SUPER_ADMIN can create/update/delete/upload an image (FarmController's
  // three write endpoints are @PreAuthorize-gated to exactly this pair) - GET itself has no
  // role restriction on the backend, but this whole page is nav-gated to admin roles anyway
  // (see Sidebar.tsx), so canWrite only needs to distinguish FARM_MANAGER/SUPER_ADMIN from
  // DELIVERY_MANAGER, who can reach this page but not mutate farm records.
  const canWrite = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [editingFarm, setEditingFarm] = useState<Farm | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Farm | null>(null)
  const [rowMenu, setRowMenu] = useState<{ anchor: HTMLElement; farm: Farm } | null>(null)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const searchParams = useMemo(
    () => ({ keyword: debouncedSearch.trim() || undefined, page, size: PAGE_SIZE }),
    [debouncedSearch, page],
  )

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['farms', 'search', searchParams],
    queryFn: () => farmService.search(searchParams),
  })
  const farms = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['farms'] })

  const deleteMutation = useMutation({
    mutationFn: (f: Farm) => farmService.delete(f.id),
    onSuccess: () => {
      enqueueSnackbar('Farm deleted successfully', { variant: 'success' })
      invalidate()
      setDeleteTarget(null)
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? "Couldn't delete this farm. Please try again.", { variant: 'error' })
      setDeleteTarget(null)
    },
  })

  const openAdd = () => { setEditingFarm(null); setFormOpen(true) }
  const openEdit = (f: Farm) => { setEditingFarm(f); setFormOpen(true) }
  const handleSaved = () => { setFormOpen(false); invalidate() }

  const columns: GridColDef<Farm>[] = [
    {
      field: 'imageUrl', headerName: '', width: 64, sortable: false, filterable: false,
      renderCell: ({ row }) => (
        <Avatar src={row.imageUrl ?? undefined} variant="rounded" sx={{ width: 36, height: 36, bgcolor: 'action.hover' }}>
          <Agriculture fontSize="small" color="disabled" />
        </Avatar>
      ),
    },
    { field: 'farmName', headerName: 'Farm Name', flex: 1, minWidth: 180 },
    { field: 'ownerName', headerName: 'Owner', flex: 1, minWidth: 160 },
    {
      field: 'location', headerName: 'Location', flex: 1, minWidth: 160,
      renderCell: ({ value }) => value || <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    {
      field: 'createdAt', headerName: 'Registered', width: 140,
      renderCell: ({ value }) => formatDate(value as string),
    },
    {
      field: 'actions', headerName: '', width: 60, sortable: false, filterable: false,
      renderCell: ({ row }) => canWrite ? (
        <IconButton size="small" onClick={(e) => setRowMenu({ anchor: e.currentTarget, farm: row })}>
          <MoreVert fontSize="small" />
        </IconButton>
      ) : null,
    },
  ]

  return (
    <Box>
      <PageHeader
        title="Farm / Business Information"
        subtitle="Manage the farm profiles registered on the platform."
        action={canWrite ? { label: 'Register Farm', icon: <Add />, onClick: openAdd } : undefined}
      />

      <BusinessSettingsCard canWrite={canWrite} />

      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          size="small"
          placeholder="Search name, owner, location…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          sx={{ minWidth: 260, flex: 1 }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
            endAdornment: search && (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearch('')}><Close fontSize="small" /></IconButton>
              </InputAdornment>
            ),
          }}
        />
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {!isLoading && farms.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <Agriculture sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {debouncedSearch ? 'No farms match your search.' : 'No farms have been registered yet.'}
          </Typography>
          {canWrite && !debouncedSearch && (
            <Button variant="contained" startIcon={<Add />} onClick={openAdd} sx={{ mt: 1 }}>Register Farm</Button>
          )}
        </Paper>
      ) : (
        <DataGrid
          rows={farms}
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
        <MenuItem onClick={() => { if (rowMenu) openEdit(rowMenu.farm); setRowMenu(null) }}>
          <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
          <ListItemText>Edit</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { if (rowMenu) setDeleteTarget(rowMenu.farm); setRowMenu(null) }} sx={{ color: 'error.main' }}>
          <ListItemIcon><Delete fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Delete</ListItemText>
        </MenuItem>
      </Menu>

      <FarmFormDialog open={formOpen} farm={editingFarm} onClose={() => setFormOpen(false)} onSaved={handleSaved} />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete Farm?"
        message={`"${deleteTarget?.farmName}" will be removed from the farm registry. This cannot be undone.`}
        confirmLabel="Delete"
        destructive
        loading={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
