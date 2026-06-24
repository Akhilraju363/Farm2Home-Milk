import {
  Box, TextField, MenuItem, Chip, InputAdornment, IconButton, Tooltip,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridValueGetterParams } from '@mui/x-data-grid'
import { Search, Refresh } from '@mui/icons-material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { customerService } from '../../services/customerService'
import type { Customer, CustomerStatus } from '../../types/customer.types'
import { statusColor } from '../../utils/formatters'

const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'All Status' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'SUSPENDED', label: 'Suspended' },
]

export function CustomersPage() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['customers', page, pageSize, statusFilter],
    queryFn: () => customerService.getAll(page, pageSize),
  })

  const rows = data?.data?.content ?? []
  const total = data?.data?.totalElements ?? 0

  const filtered = search
    ? rows.filter(
        (c) =>
          c.firstName.toLowerCase().includes(search.toLowerCase()) ||
          c.lastName.toLowerCase().includes(search.toLowerCase()) ||
          c.mobile.includes(search) ||
          (c.email ?? '').toLowerCase().includes(search.toLowerCase()),
      )
    : rows

  const columns: GridColDef[] = [
    { field: 'customerCode', headerName: 'Code', width: 110 },
    {
      field: 'name', headerName: 'Name', flex: 1, minWidth: 140,
      valueGetter: ({ row }: GridValueGetterParams<any, Customer>) => `${row.firstName} ${row.lastName}`,
    },
    { field: 'mobile', headerName: 'Mobile', width: 130 },
    { field: 'email', headerName: 'Email', flex: 1, minWidth: 160 },
    {
      field: 'status', headerName: 'Status', width: 120,
      renderCell: ({ value }) => (
        <Chip label={value} color={statusColor(value as CustomerStatus)} size="small" />
      ),
    },
  ]

  return (
    <Box>
      <PageHeader title="Customers" subtitle={`${total.toLocaleString()} total customers`} />

      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search name, mobile, email…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: 260 }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
          }}
        />
        <TextField
          select size="small" value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          sx={{ minWidth: 140 }}
          label="Status"
        >
          {STATUS_OPTIONS.map((o) => (
            <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
          ))}
        </TextField>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      <DataGrid
        rows={filtered}
        columns={columns}
        loading={isLoading}
        rowCount={total}
        paginationMode="server"
        paginationModel={{ page, pageSize }}
        onPaginationModelChange={({ page: p, pageSize: ps }) => { setPage(p); setPageSize(ps) }}
        pageSizeOptions={[10, 20, 50]}
        autoHeight
        disableRowSelectionOnClick
        sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
      />
    </Box>
  )
}
