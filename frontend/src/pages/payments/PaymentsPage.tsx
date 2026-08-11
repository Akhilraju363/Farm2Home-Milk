import {
  Box, TextField, IconButton, Tooltip, Button, Typography, Paper, Pagination, Alert,
  Chip, Tabs, Tab, Grid, Card, CircularProgress,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { Refresh, Download, Payment as PaymentIcon } from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { CustomerAutocomplete } from '../../components/subscription/CustomerAutocomplete'
import { useAuth } from '../../hooks/useAuth'
import { paymentService } from '../../services/paymentService'
import { downloadBlob } from '../../utils/download'
import { formatCurrency, formatDateTime, statusColor } from '../../utils/formatters'
import { PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '../../types/payment.types'
import type { Payment, PaymentReportRow, PaymentStatus } from '../../types/payment.types'
import type { Customer } from '../../types/customer.types'

const PAGE_SIZE = 20

const STATUS_TABS: Array<{ value: PaymentStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'SUCCESS', label: 'Success' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDED', label: 'Refunded' },
]

// SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER - matches GET /payments/reports's own
// @PreAuthorize exactly (same set useAuth().isAdmin() already encodes).
export function PaymentsPage() {
  const { isAdmin } = useAuth()
  const canManage = isAdmin()

  return canManage ? <AdminPaymentsTable /> : <MyPaymentsList />
}

// ── Customer: plain list, no filters - GET /payments supports pagination only. ──────────────
function MyPaymentsList() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['payments', 'mine', page],
    queryFn: () => paymentService.getAll({ page, size: PAGE_SIZE }),
  })

  const payments = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const columns: GridColDef<Payment>[] = [
    { field: 'paymentReference', headerName: 'Reference', width: 190 },
    {
      field: 'orderId', headerName: 'Order', width: 150,
      renderCell: ({ value }) => (
        <Typography
          variant="caption" sx={{ fontFamily: 'monospace', color: 'primary.main', cursor: 'pointer' }}
          onClick={(e) => { e.stopPropagation(); navigate(`/orders/${value}`) }}
        >
          {String(value).slice(0, 8)}…
        </Typography>
      ),
    },
    { field: 'amount', headerName: 'Amount', width: 110, renderCell: ({ value }) => formatCurrency(value as number) },
    { field: 'paymentMethod', headerName: 'Method', width: 130, renderCell: ({ value }) => PAYMENT_METHOD_LABELS[value as keyof typeof PAYMENT_METHOD_LABELS] },
    {
      field: 'paymentStatus', headerName: 'Status', width: 130,
      renderCell: ({ value }) => <Chip label={PAYMENT_STATUS_LABELS[value as PaymentStatus]} size="small" color={statusColor(value as string)} sx={{ fontWeight: 600 }} />,
    },
    { field: 'createdAt', headerName: 'Created', width: 170, renderCell: ({ value }) => formatDateTime(value as string) },
  ]

  return (
    <Box>
      <PageHeader title="My Payments" subtitle="Payments made against your orders." />

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Couldn't load your payments. Please check your connection and try again.
        </Alert>
      ) : !isLoading && payments.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <PaymentIcon sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700}>No payments yet.</Typography>
        </Paper>
      ) : (
        <DataGrid
          rows={payments}
          columns={columns}
          loading={isLoading || isFetching}
          rowHeight={56}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          onRowClick={(params) => navigate(`/payments/${params.id}`)}
          sx={{
            bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
            '& .MuiDataGrid-row': { cursor: 'pointer' },
          }}
        />
      )}

      {!isLoading && !isError && totalElements > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} payment{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}
    </Box>
  )
}

// ── Admin: filtered table sourced from GET /payments/reports - the only endpoint that actually
// offers dateFrom/dateTo/status/customerId filters; the plain GET /payments has none. ──────────
function AdminPaymentsTable() {
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()

  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<PaymentStatus | ''>('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [exporting, setExporting] = useState<'CSV' | 'EXCEL' | 'PDF' | null>(null)

  const filterParams = useMemo(() => ({
    status: (status || undefined) as PaymentStatus | undefined,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    customerId: customer?.id || undefined,
    page, size: PAGE_SIZE,
  }), [status, dateFrom, dateTo, customer, page])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['payments', 'reports', filterParams],
    queryFn: () => paymentService.getReport(filterParams),
  })

  const rows = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0
  const summary = data?.data.data.summary

  const hasActiveFilters = Boolean(status || dateFrom || dateTo || customer)
  const resetFilters = () => { setStatus(''); setDateFrom(''); setDateTo(''); setCustomer(null); setPage(0) }

  const handleExport = async (format: 'CSV' | 'EXCEL' | 'PDF') => {
    setExporting(format)
    try {
      const { blob, filename } = await paymentService.export({ ...filterParams, format })
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar("Couldn't export payments. Please try again.", { variant: 'error' })
    } finally {
      setExporting(null)
    }
  }

  const columns: GridColDef<PaymentReportRow>[] = [
    {
      field: 'paymentId', headerName: 'Payment ID', width: 130,
      renderCell: ({ value }) => <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{String(value).slice(0, 8)}…</Typography>,
    },
    {
      field: 'orderId', headerName: 'Order', width: 130,
      renderCell: ({ value }) => (
        <Typography
          variant="caption" sx={{ fontFamily: 'monospace', color: 'primary.main', cursor: 'pointer' }}
          onClick={(e) => { e.stopPropagation(); navigate(`/orders/${value}`) }}
        >
          {String(value).slice(0, 8)}…
        </Typography>
      ),
    },
    {
      field: 'customerId', headerName: 'Customer', width: 130,
      renderCell: ({ value }) => <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{String(value).slice(0, 8)}…</Typography>,
    },
    { field: 'amount', headerName: 'Amount', width: 110, renderCell: ({ value }) => formatCurrency(value as number) },
    { field: 'paymentMethod', headerName: 'Method', width: 120, renderCell: ({ value }) => PAYMENT_METHOD_LABELS[value as keyof typeof PAYMENT_METHOD_LABELS] ?? value },
    {
      field: 'paymentStatus', headerName: 'Status', width: 130,
      renderCell: ({ value }) => <Chip label={PAYMENT_STATUS_LABELS[value as PaymentStatus] ?? value} size="small" color={statusColor(value as string)} sx={{ fontWeight: 600 }} />,
    },
    { field: 'createdAt', headerName: 'Created', width: 170, renderCell: ({ value }) => formatDateTime(value as string) },
  ]

  return (
    <Box>
      <PageHeader title="Payment Management" subtitle="Track and manage Farm2Home payments." />

      {summary && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={6} sm={4}>
            <Card variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="h6" fontWeight={700}>{summary.totalPayments.toLocaleString()}</Typography>
              <Typography variant="caption" color="text.secondary">Payments (this view)</Typography>
            </Card>
          </Grid>
          <Grid item xs={6} sm={4}>
            <Card variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="h6" fontWeight={700}>{formatCurrency(summary.totalAmount)}</Typography>
              <Typography variant="caption" color="text.secondary">Total Amount</Typography>
            </Card>
          </Grid>
          <Grid item xs={12} sm={4}>
            <Card variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="h6" fontWeight={700}>{formatCurrency(summary.successAmount)}</Typography>
              <Typography variant="caption" color="text.secondary">Successful Amount</Typography>
            </Card>
          </Grid>
        </Grid>
      )}

      <Tabs value={status} onChange={(_e, v) => { setStatus(v); setPage(0) }} sx={{ mb: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
        {STATUS_TABS.map((t) => <Tab key={t.value} value={t.value} label={t.label} />)}
      </Tabs>

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Box sx={{ minWidth: 240 }}>
          <CustomerAutocomplete value={customer} onChange={(c) => { setCustomer(c); setPage(0) }} />
        </Box>
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
        <Button
          variant="outlined" startIcon={exporting === 'CSV' ? <CircularProgress size={16} /> : <Download />}
          onClick={() => handleExport('CSV')} disabled={!!exporting}
        >
          CSV
        </Button>
        <Button
          variant="outlined" startIcon={exporting === 'EXCEL' ? <CircularProgress size={16} /> : <Download />}
          onClick={() => handleExport('EXCEL')} disabled={!!exporting}
        >
          Excel
        </Button>
        <Button
          variant="outlined" startIcon={exporting === 'PDF' ? <CircularProgress size={16} /> : <Download />}
          onClick={() => handleExport('PDF')} disabled={!!exporting}
        >
          PDF
        </Button>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Couldn't load payments. Please check your connection and try again.
        </Alert>
      ) : !isLoading && rows.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <PaymentIcon sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No payments match your filters.' : 'No payments found.'}
          </Typography>
          {hasActiveFilters && <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>}
        </Paper>
      ) : (
        <DataGrid
          rows={rows}
          columns={columns}
          getRowId={(row) => row.paymentId}
          loading={isLoading || isFetching}
          rowHeight={56}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          onRowClick={(params) => navigate(`/payments/${params.id}`)}
          sx={{
            bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider',
            '& .MuiDataGrid-row': { cursor: 'pointer' },
          }}
        />
      )}

      {!isLoading && !isError && totalElements > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} payment{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
        </Box>
      )}
    </Box>
  )
}
