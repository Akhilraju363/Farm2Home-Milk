import { Box, Grid, Card, Typography, TextField, MenuItem } from '@mui/material'
import {
  ResponsiveContainer, LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
} from 'recharts'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { PageHeader } from '../../components/common/PageHeader'
import { paymentService } from '../../services/paymentService'
import { formatCurrency } from '../../utils/formatters'
import { GRANULARITIES, GRANULARITY_LABELS } from '../../types/payment.types'
import type { Granularity } from '../../types/payment.types'

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2.5 }}>
      <Typography variant="subtitle1" fontWeight={600} mb={2}>{title}</Typography>
      {children}
    </Card>
  )
}

// SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER only - matches the analytics endpoints' shared
// @PreAuthorize exactly.
export function PaymentAnalyticsPage() {
  const [granularity, setGranularity] = useState<Granularity>('DAILY')
  const [dateFrom, setDateFrom] = useState(dayjs().subtract(29, 'day').format('YYYY-MM-DD'))
  const [dateTo, setDateTo] = useState(dayjs().format('YYYY-MM-DD'))

  const { data: revenueData, isLoading: revenueLoading } = useQuery({
    queryKey: ['payments', 'analytics', 'revenue-trend', granularity, dateFrom, dateTo],
    queryFn: () => paymentService.getRevenueTrend(granularity, dateFrom, dateTo),
  })

  const { data: analyticsData, isLoading: analyticsLoading } = useQuery({
    queryKey: ['payments', 'analytics', 'payment-trend', granularity, dateFrom, dateTo],
    queryFn: () => paymentService.getPaymentAnalytics(granularity, dateFrom, dateTo),
  })

  const revenueChart = (revenueData?.data.data.points ?? []).map((p) => ({
    period: dayjs(p.period).format('DD MMM'),
    revenue: Number(p.revenue),
  }))

  const analyticsChart = (analyticsData?.data.data.points ?? []).map((p) => ({
    period: dayjs(p.period).format('DD MMM'),
    success: p.successCount,
    failed: p.failedCount,
    total: p.totalPayments,
  }))

  return (
    <Box>
      <PageHeader title="Payment Analytics" subtitle="Revenue and transaction trends over time." />

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          select size="small" label="Granularity" value={granularity}
          onChange={(e) => setGranularity(e.target.value as Granularity)} sx={{ minWidth: 140 }}
        >
          {GRANULARITIES.map((g) => <MenuItem key={g} value={g}>{GRANULARITY_LABELS[g]}</MenuItem>)}
        </TextField>
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
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <ChartCard title="Revenue Trend (SUCCESS payments)">
            {!revenueLoading && revenueChart.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No revenue in this range.</Typography>
            ) : (
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={revenueChart}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(v: number) => [formatCurrency(v), 'Revenue']} />
                  <Line type="monotone" dataKey="revenue" stroke="#2E7D32" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </ChartCard>
        </Grid>

        <Grid item xs={12} lg={6}>
          <ChartCard title="Payment Trend (all statuses)">
            {!analyticsLoading && analyticsChart.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No payments in this range.</Typography>
            ) : (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={analyticsChart}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="success" name="Success" stackId="a" fill="#2E7D32" />
                  <Bar dataKey="failed" name="Failed" stackId="a" fill="#C62828" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </ChartCard>
        </Grid>
      </Grid>
    </Box>
  )
}
