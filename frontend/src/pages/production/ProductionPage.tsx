import { Box, TextField, MenuItem, Chip, Tooltip, IconButton } from '@mui/material'
import { DataGrid, type GridColDef, type GridValueGetterParams } from '@mui/x-data-grid'
import { Refresh } from '@mui/icons-material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { productionService } from '../../services/productionService'
import { formatDate } from '../../utils/formatters'
import type { MilkSession, QualityGrade } from '../../types/production.types'

const SESSION_OPTIONS = [
  { value: '', label: 'All Sessions' },
  { value: 'MORNING', label: 'Morning' },
  { value: 'EVENING', label: 'Evening' },
]

const GRADE_COLORS: Record<string, 'success' | 'warning' | 'error'> = {
  A: 'success', B: 'warning', C: 'error',
}

export function ProductionPage() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [session, setSession] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['productions', page, pageSize],
    queryFn: () => productionService.getAll({ page, size: pageSize }),
  })

  const allRows = data?.data?.content ?? []
  const total = data?.data?.totalElements ?? 0

  const rows = session ? allRows.filter((r) => r.session === session) : allRows

  const columns: GridColDef[] = [
    {
      field: 'collectionDate', headerName: 'Date', width: 120,
      valueGetter: ({ value }: GridValueGetterParams) => formatDate(value as string),
    },
    {
      field: 'session', headerName: 'Session', width: 110,
      renderCell: ({ value }) => (
        <Chip
          label={value as MilkSession}
          size="small"
          color={value === 'MORNING' ? 'warning' : 'info'}
        />
      ),
    },
    {
      field: 'quantityLiters', headerName: 'Quantity (L)', width: 130, type: 'number',
      valueGetter: ({ value }: GridValueGetterParams) => Number(value).toFixed(2),
    },
    {
      field: 'fatPercentage', headerName: 'Fat %', width: 100, type: 'number',
      valueGetter: ({ value }: GridValueGetterParams) => value != null ? `${Number(value).toFixed(2)}%` : '—',
    },
    {
      field: 'snfPercentage', headerName: 'SNF %', width: 100, type: 'number',
      valueGetter: ({ value }: GridValueGetterParams) => value != null ? `${Number(value).toFixed(2)}%` : '—',
    },
    {
      field: 'qualityGrade', headerName: 'Grade', width: 90,
      renderCell: ({ value }) =>
        value ? (
          <Chip label={`Grade ${value}`} color={GRADE_COLORS[value as QualityGrade]} size="small" />
        ) : null,
    },
    { field: 'collectedBy', headerName: 'Collected By', flex: 1, minWidth: 130 },
    { field: 'createdBy', headerName: 'Recorded By', width: 130 },
  ]

  return (
    <Box>
      <PageHeader
        title="Milk Production"
        subtitle={`${total.toLocaleString()} records`}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          select size="small" label="Session" value={session}
          onChange={(e) => setSession(e.target.value)} sx={{ minWidth: 150 }}
        >
          {SESSION_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
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
        sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
      />
    </Box>
  )
}
