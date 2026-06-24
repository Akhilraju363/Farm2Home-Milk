import { Box, TextField, MenuItem, Chip, Tooltip, IconButton } from '@mui/material'
import { DataGrid, type GridColDef, type GridValueGetterParams } from '@mui/x-data-grid'
import { Refresh } from '@mui/icons-material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { subscriptionService } from '../../services/subscriptionService'
import { formatDate, statusColor } from '../../utils/formatters'
import type { SubscriptionStatus } from '../../types/subscription.types'

const STATUS_OPTIONS = [
  { value: '', label: 'All Status' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PAUSED', label: 'Paused' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'EXPIRED', label: 'Expired' },
]

const MILK_TYPE_LABELS: Record<string, string> = {
  FULL_CREAM: 'Full Cream', TONED: 'Toned',
  DOUBLE_TONED: 'Double Toned', SKIMMED: 'Skimmed',
}

const SCHEDULE_LABELS: Record<string, string> = {
  DAILY: 'Daily', ALTERNATE_DAY: 'Alternate Day', WEEKLY: 'Weekly',
}

export function SubscriptionsPage() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [status, setStatus] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['subscriptions', page, pageSize, status],
    queryFn: () => subscriptionService.getAll({ page, size: pageSize, status: status || undefined }),
  })

  const rows = data?.data?.content ?? []
  const total = data?.data?.totalElements ?? 0

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 100, valueGetter: ({ value }: GridValueGetterParams) => String(value).slice(0, 8) + '…' },
    {
      field: 'milkType', headerName: 'Milk Type', width: 140,
      valueGetter: ({ value }: GridValueGetterParams) => MILK_TYPE_LABELS[value as string] ?? value,
    },
    { field: 'quantity', headerName: 'Qty (L)', width: 90, type: 'number' },
    {
      field: 'scheduleType', headerName: 'Schedule', width: 140,
      valueGetter: ({ value }: GridValueGetterParams) => SCHEDULE_LABELS[value as string] ?? value,
    },
    {
      field: 'startDate', headerName: 'Start Date', width: 120,
      valueGetter: ({ value }: GridValueGetterParams) => formatDate(value as string),
    },
    {
      field: 'endDate', headerName: 'End Date', width: 120,
      valueGetter: ({ value }: GridValueGetterParams) => formatDate(value as string | undefined),
    },
    {
      field: 'status', headerName: 'Status', width: 120,
      renderCell: ({ value }) => (
        <Chip label={value} color={statusColor(value as SubscriptionStatus)} size="small" />
      ),
    },
  ]

  return (
    <Box>
      <PageHeader title="Subscriptions" subtitle={`${total.toLocaleString()} total subscriptions`} />

      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          select size="small" label="Status" value={status}
          onChange={(e) => setStatus(e.target.value)}
          sx={{ minWidth: 160 }}
        >
          {STATUS_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
        </TextField>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      <DataGrid
        rows={rows} columns={columns} loading={isLoading}
        rowCount={total} paginationMode="server"
        paginationModel={{ page, pageSize }}
        onPaginationModelChange={({ page: p, pageSize: ps }) => { setPage(p); setPageSize(ps) }}
        pageSizeOptions={[10, 20, 50]}
        autoHeight disableRowSelectionOnClick
        sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
      />
    </Box>
  )
}
