import { Box, TextField, MenuItem, Chip, Tooltip, IconButton } from '@mui/material'
import { DataGrid, type GridColDef, type GridValueGetterParams } from '@mui/x-data-grid'
import { Refresh } from '@mui/icons-material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { paymentService } from '../../services/paymentService'
import { formatDateTime, formatCurrency, statusColor } from '../../utils/formatters'
import type { PaymentStatus } from '../../types/payment.types'

const STATUS_OPTIONS = [
  { value: '', label: 'All Status' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'SUCCESS', label: 'Success' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDED', label: 'Refunded' },
]

const METHOD_OPTIONS = [
  { value: '', label: 'All Methods' },
  { value: 'UPI', label: 'UPI' },
  { value: 'RAZORPAY', label: 'Razorpay' },
  { value: 'WALLET', label: 'Wallet' },
  { value: 'CASH', label: 'Cash' },
]

const METHOD_COLORS: Record<string, string> = {
  UPI: '#6A1B9A', RAZORPAY: '#1565C0', WALLET: '#00695C', CASH: '#4E342E',
}

export function PaymentsPage() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [status, setStatus] = useState('')
  const [method, setMethod] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['payments', page, pageSize, status, method],
    queryFn: () =>
      paymentService.getAll({
        page, size: pageSize,
        status: status || undefined,
        paymentMethod: method || undefined,
      }),
  })

  const rows = data?.data?.content ?? []
  const total = data?.data?.totalElements ?? 0

  const columns: GridColDef[] = [
    { field: 'paymentReference', headerName: 'Reference', width: 160 },
    {
      field: 'amount', headerName: 'Amount', width: 120,
      valueGetter: ({ value }: GridValueGetterParams) => formatCurrency(value as number),
    },
    {
      field: 'paymentMethod', headerName: 'Method', width: 120,
      renderCell: ({ value }) => (
        <Chip
          label={value}
          size="small"
          sx={{ bgcolor: `${METHOD_COLORS[value] ?? '#555'}22`, color: METHOD_COLORS[value] ?? '#555', fontWeight: 600, fontSize: 11 }}
        />
      ),
    },
    {
      field: 'paymentStatus', headerName: 'Status', width: 120,
      renderCell: ({ value }) => (
        <Chip label={value} color={statusColor(value as PaymentStatus)} size="small" />
      ),
    },
    {
      field: 'paidAt', headerName: 'Paid At', flex: 1, minWidth: 160,
      valueGetter: ({ value }: GridValueGetterParams) => formatDateTime(value as string | undefined),
    },
    {
      field: 'createdAt', headerName: 'Created', flex: 1, minWidth: 160,
      valueGetter: ({ value }: GridValueGetterParams) => formatDateTime(value as string),
    },
  ]

  return (
    <Box>
      <PageHeader title="Payments" subtitle={`${total.toLocaleString()} total payments`} />

      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField
          select size="small" label="Status" value={status}
          onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 140 }}
        >
          {STATUS_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
        </TextField>
        <TextField
          select size="small" label="Method" value={method}
          onChange={(e) => setMethod(e.target.value)} sx={{ minWidth: 140 }}
        >
          {METHOD_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
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
