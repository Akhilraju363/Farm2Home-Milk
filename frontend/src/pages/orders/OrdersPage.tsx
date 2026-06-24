import { Box, TextField, MenuItem, Chip, Tooltip, IconButton } from '@mui/material'
import { DataGrid, type GridColDef, type GridValueGetterParams } from '@mui/x-data-grid'
import { Refresh } from '@mui/icons-material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { orderService } from '../../services/orderService'
import { formatDate, formatCurrency, statusColor } from '../../utils/formatters'
import type { OrderStatus } from '../../types/order.types'

const STATUS_OPTIONS = [
  { value: '', label: 'All Status' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

const TYPE_LABELS: Record<string, string> = { SUBSCRIPTION: 'Subscription', ONE_TIME: 'One-Time' }

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [status, setStatus] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['orders', page, pageSize, status],
    queryFn: () => orderService.getAll({ page, size: pageSize, status: status || undefined }),
  })

  const rows = data?.data?.content ?? []
  const total = data?.data?.totalElements ?? 0

  const columns: GridColDef[] = [
    { field: 'orderNumber', headerName: 'Order #', width: 140 },
    {
      field: 'orderDate', headerName: 'Date', width: 120,
      valueGetter: ({ value }: GridValueGetterParams) => formatDate(value as string),
    },
    {
      field: 'orderType', headerName: 'Type', width: 130,
      valueGetter: ({ value }: GridValueGetterParams) => TYPE_LABELS[value as string] ?? value,
    },
    {
      field: 'totalAmount', headerName: 'Amount', width: 120, type: 'number',
      valueGetter: ({ value }: GridValueGetterParams) => formatCurrency(value as number),
    },
    {
      field: 'status', headerName: 'Status', width: 150,
      renderCell: ({ value }) => (
        <Chip label={String(value).replace(/_/g, ' ')} color={statusColor(value as OrderStatus)} size="small" />
      ),
    },
    {
      field: 'items', headerName: 'Items', width: 80, type: 'number',
      valueGetter: ({ value }: GridValueGetterParams) => (value as unknown[])?.length ?? 0,
    },
  ]

  return (
    <Box>
      <PageHeader title="Orders" subtitle={`${total.toLocaleString()} total orders`} />

      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          select size="small" label="Status" value={status}
          onChange={(e) => setStatus(e.target.value)}
          sx={{ minWidth: 180 }}
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
