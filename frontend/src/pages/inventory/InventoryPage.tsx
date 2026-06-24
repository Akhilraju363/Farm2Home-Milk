import { Box, TextField, MenuItem, Chip, Tooltip, IconButton, Alert } from '@mui/material'
import { DataGrid, type GridColDef, type GridValueGetterParams } from '@mui/x-data-grid'
import { Refresh, Warning } from '@mui/icons-material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { inventoryService } from '../../services/inventoryService'
import { formatCurrency } from '../../utils/formatters'
import type { ItemType } from '../../types/inventory.types'

const TYPE_OPTIONS = [
  { value: '', label: 'All Types' },
  { value: 'FEED', label: 'Feed' },
  { value: 'MEDICINE', label: 'Medicine' },
  { value: 'EQUIPMENT', label: 'Equipment' },
]

const TYPE_COLORS: Record<string, string> = {
  FEED: '#2E7D32', MEDICINE: '#C62828', EQUIPMENT: '#1565C0',
}

export function InventoryPage() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [typeFilter, setTypeFilter] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['inventory', page, pageSize, typeFilter],
    queryFn: () => inventoryService.getAll({ page, size: pageSize, type: typeFilter || undefined }),
  })

  const { data: lowStockData } = useQuery({
    queryKey: ['inventory', 'low-stock'],
    queryFn: () => inventoryService.getLowStock(),
  })

  const rows = data?.data?.content ?? []
  const total = data?.data?.totalElements ?? 0
  const lowStockCount = lowStockData?.data?.length ?? 0

  const columns: GridColDef[] = [
    { field: 'itemName', headerName: 'Item Name', flex: 1, minWidth: 160 },
    {
      field: 'itemType', headerName: 'Type', width: 120,
      renderCell: ({ value }) => (
        <Chip
          label={value} size="small"
          sx={{ bgcolor: `${TYPE_COLORS[value as ItemType]}22`, color: TYPE_COLORS[value as ItemType], fontWeight: 600 }}
        />
      ),
    },
    { field: 'quantity', headerName: 'Quantity', width: 110, type: 'number' },
    { field: 'unit', headerName: 'Unit', width: 80 },
    { field: 'reorderLevel', headerName: 'Reorder Level', width: 130, type: 'number' },
    {
      field: 'belowReorderLevel', headerName: 'Alert', width: 80,
      renderCell: ({ value }) =>
        value ? <Warning color="error" fontSize="small" /> : null,
    },
    {
      field: 'unitPrice', headerName: 'Unit Price', width: 120,
      valueGetter: ({ value }: GridValueGetterParams) => value != null ? formatCurrency(value as number) : '—',
    },
    { field: 'supplier', headerName: 'Supplier', flex: 1, minWidth: 130 },
  ]

  return (
    <Box>
      <PageHeader title="Inventory" subtitle={`${total.toLocaleString()} items`} />

      {lowStockCount > 0 && (
        <Alert severity="warning" icon={<Warning />} sx={{ mb: 2 }}>
          <strong>{lowStockCount} item(s)</strong> are below reorder level and need restocking.
        </Alert>
      )}

      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          select size="small" label="Type" value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)} sx={{ minWidth: 150 }}
        >
          {TYPE_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
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
        pageSizeOptions={[20, 50, 100]}
        autoHeight disableRowSelectionOnClick
        getRowClassName={({ row }) => row.belowReorderLevel ? 'low-stock-row' : ''}
        sx={{
          bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
          '& .low-stock-row': { bgcolor: '#FFF3E0' },
        }}
      />
    </Box>
  )
}
