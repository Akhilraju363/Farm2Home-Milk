import { Box, Grid, Card, Typography, TextField, Button, CircularProgress } from '@mui/material'
import { Download } from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { CustomerAutocomplete } from '../../components/subscription/CustomerAutocomplete'
import { paymentService } from '../../services/paymentService'
import { downloadBlob } from '../../utils/download'
import { formatCurrency } from '../../utils/formatters'
import { PAYMENT_STATUSES, PAYMENT_STATUS_LABELS } from '../../types/payment.types'
import type { Customer } from '../../types/customer.types'

function KpiCard({ label, value }: { label: string; value: string }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, textAlign: 'center' }}>
      <Typography variant="h6" fontWeight={700} color="primary.main">{value}</Typography>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
    </Card>
  )
}

// SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER only - matches GET /payments/summary,
// /payments/reports, and /payments/export's shared @PreAuthorize exactly.
export function PaymentReportsPage() {
  const { enqueueSnackbar } = useSnackbar()
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [exporting, setExporting] = useState<'CSV' | 'EXCEL' | 'PDF' | null>(null)

  const filterParams = useMemo(() => ({
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    customerId: customer?.id || undefined,
  }), [dateFrom, dateTo, customer])

  const { data: summaryData } = useQuery({
    queryKey: ['payments', 'summary'],
    queryFn: () => paymentService.getSummary(),
  })

  // Real, server-reported totalElements per status for the current filter set - not a
  // client-side count of a capped page.
  const statusCountQueries = useQueries({
    queries: PAYMENT_STATUSES.map((status) => ({
      queryKey: ['payments', 'reports', 'count', status, filterParams],
      queryFn: () => paymentService.getReport({ ...filterParams, status, size: 1 }),
    })),
  })

  const { data: reportData } = useQuery({
    queryKey: ['payments', 'reports', 'full', filterParams],
    queryFn: () => paymentService.getReport({ ...filterParams, size: 1 }),
  })
  const reportSummary = reportData?.data.data.summary

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

  return (
    <Box>
      <PageHeader title="Payment Reports" subtitle="Revenue and payment totals across Farm2Home." />

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Box sx={{ minWidth: 240 }}>
          <CustomerAutocomplete value={customer} onChange={setCustomer} />
        </Box>
        <TextField
          size="small" label="From" type="date" value={dateFrom}
          onChange={(e) => setDateFrom(e.target.value)}
          InputLabelProps={{ shrink: true }} sx={{ minWidth: 150 }}
        />
        <TextField
          size="small" label="To" type="date" value={dateTo}
          onChange={(e) => setDateTo(e.target.value)}
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
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Today's Revenue" value={formatCurrency(summaryData?.data.data.revenueToday ?? 0)} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Monthly Revenue" value={formatCurrency(summaryData?.data.data.revenueThisMonth ?? 0)} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Total Payments (filtered)" value={(reportSummary?.totalPayments ?? 0).toLocaleString()} />
        </Grid>
        <Grid item xs={6} sm={3}>
          <KpiCard label="Successful Amount (filtered)" value={formatCurrency(reportSummary?.successAmount ?? 0)} />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        {PAYMENT_STATUSES.map((status, i) => (
          <Grid item xs={6} sm={3} key={status}>
            <KpiCard
              label={`${PAYMENT_STATUS_LABELS[status]} Payments`}
              value={(statusCountQueries[i].data?.data.data.totalElements ?? 0).toLocaleString()}
            />
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}
