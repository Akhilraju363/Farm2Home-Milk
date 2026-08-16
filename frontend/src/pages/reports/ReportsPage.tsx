import { Box, Grid, Card, Typography } from '@mui/material'
import {
  ResponsiveContainer, LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, PieChart, Pie, Cell,
} from 'recharts'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs'
import dayjs, { type Dayjs } from 'dayjs'
import { PageHeader } from '../../components/common/PageHeader'
import { productionService } from '../../services/productionService'
import { orderService } from '../../services/orderService'
import { subscriptionService } from '../../services/subscriptionService'
import { paymentService } from '../../services/paymentService'
import { formatCurrency } from '../../utils/formatters'

const COLORS = ['#2E7D32', '#1565C0', '#C62828', '#F9A825', '#6A1B9A', '#00695C']

function ReportCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2.5 }}>
      <Typography variant="subtitle1" fontWeight={600} mb={2}>{title}</Typography>
      {children}
    </Card>
  )
}

export function ReportsPage() {
  const [fromDate, setFromDate] = useState<Dayjs>(dayjs().subtract(29, 'day'))
  const [toDate, setToDate] = useState<Dayjs>(dayjs())

  const from = fromDate.format('YYYY-MM-DD')
  const to   = toDate.format('YYYY-MM-DD')

  const { data: productionData } = useQuery({
    queryKey: ['reports', 'production', from, to],
    queryFn: () => productionService.getDailySummary(from, to),
  })

  const { data: ordersData } = useQuery({
    queryKey: ['reports', 'orders', from, to],
    queryFn: () => orderService.search({ size: 500 }),
  })

  const { data: subsData } = useQuery({
    queryKey: ['reports', 'subs'],
    queryFn: () => subscriptionService.search({ size: 1 }),
  })

  const { data: paymentsData } = useQuery({
    queryKey: ['reports', 'payments', from, to],
    // GET /payments has no status filter (only /payments/reports does, and that's admin-only -
    // this page is open to every role) - fetch the caller's visible payments and filter to
    // SUCCESS client-side instead of asking the server to do it.
    queryFn: () => paymentService.getAll({ size: 500 }),
  })

  // Production chart
  const productionChart = (productionData?.data.data ?? []).map((d) => ({
    date: dayjs(d.date).format('DD MMM'),
    liters: Number(Number(d.totalLiters).toFixed(1)),
    records: d.recordCount,
  }))

  // Revenue by method (SUCCESS payments only - filtered client-side, see query comment above)
  const successPayments = (paymentsData?.data.data.content ?? []).filter((p) => p.paymentStatus === 'SUCCESS')
  const revenueByMethodMap: Record<string, number> = {}
  for (const p of successPayments) {
    revenueByMethodMap[p.paymentMethod] = (revenueByMethodMap[p.paymentMethod] ?? 0) + Number(p.amount)
  }
  const revenueByMethodData = Object.entries(revenueByMethodMap).map(([name, value]) => ({ name, value }))

  // Orders by type
  const orderTypeMap: Record<string, number> = {}
  for (const o of ordersData?.data.data.content ?? []) {
    orderTypeMap[o.orderType] = (orderTypeMap[o.orderType] ?? 0) + 1
  }
  const orderTypeData = Object.entries(orderTypeMap).map(([name, value]) => ({ name, value }))

  // Order status over time (aggregate by day)
  const ordersByDateMap: Record<string, { delivered: number; cancelled: number; total: number }> = {}
  for (const o of ordersData?.data.data.content ?? []) {
    const d = dayjs(o.orderDate).format('DD MMM')
    if (!ordersByDateMap[d]) ordersByDateMap[d] = { delivered: 0, cancelled: 0, total: 0 }
    ordersByDateMap[d].total++
    if (o.status === 'DELIVERED') ordersByDateMap[d].delivered++
    if (o.status === 'CANCELLED') ordersByDateMap[d].cancelled++
  }
  const ordersTimeData = Object.entries(ordersByDateMap)
    .sort((a, b) => dayjs(a[0], 'DD MMM').valueOf() - dayjs(b[0], 'DD MMM').valueOf())
    .map(([date, v]) => ({ date, ...v }))

  // Summary totals
  const totalRevenue = successPayments.reduce((s, p) => s + Number(p.amount), 0)
  const totalOrders = ordersData?.data.data.totalElements ?? 0
  const totalSubs = subsData?.data.data.totalElements ?? 0
  const totalProduction = (productionData?.data.data ?? []).reduce((s, d) => s + Number(d.totalLiters), 0)

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        <PageHeader title="Reports & Analytics" />

        {/* Date range picker */}
        <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <DatePicker
            label="From Date"
            value={fromDate}
            onChange={(d) => d && setFromDate(d)}
            slotProps={{ textField: { size: 'small' } }}
          />
          <DatePicker
            label="To Date"
            value={toDate}
            onChange={(d) => d && setToDate(d)}
            slotProps={{ textField: { size: 'small' } }}
          />
        </Box>

        {/* KPI row */}
        <Grid container spacing={2} mb={3}>
          {[
            { label: 'Total Revenue', value: formatCurrency(totalRevenue) },
            { label: 'Total Orders', value: totalOrders.toLocaleString() },
            { label: 'Active Subscriptions', value: totalSubs.toLocaleString() },
            { label: 'Milk Produced', value: `${totalProduction.toFixed(1)} L` },
          ].map((kpi) => (
            <Grid item xs={6} md={3} key={kpi.label}>
              <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, textAlign: 'center' }}>
                <Typography variant="h5" fontWeight={700} color="primary.main">{kpi.value}</Typography>
                <Typography variant="caption" color="text.secondary">{kpi.label}</Typography>
              </Card>
            </Grid>
          ))}
        </Grid>

        {/* Charts */}
        <Grid container spacing={2}>
          <Grid item xs={12} lg={8}>
            <ReportCard title="Milk Production Trend">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={productionChart}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} unit=" L" />
                  <Tooltip formatter={(v: number) => [`${v} L`, 'Production']} />
                  <Line type="monotone" dataKey="liters" stroke="#2E7D32" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </ReportCard>
          </Grid>

          <Grid item xs={12} lg={4}>
            <ReportCard title="Revenue by Payment Method">
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie data={revenueByMethodData} cx="50%" cy="50%" outerRadius={90} dataKey="value" paddingAngle={3}>
                    {revenueByMethodData.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(v: number) => [formatCurrency(v), 'Revenue']} />
                  <Legend formatter={(v) => <span style={{ fontSize: 12 }}>{v}</span>} />
                </PieChart>
              </ResponsiveContainer>
            </ReportCard>
          </Grid>

          <Grid item xs={12} lg={6}>
            <ReportCard title="Orders Over Time">
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={ordersTimeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                  <Tooltip />
                  <Legend formatter={(v) => <span style={{ fontSize: 12 }}>{v}</span>} />
                  <Bar dataKey="delivered" name="Delivered" fill="#2E7D32" radius={[2, 2, 0, 0]} />
                  <Bar dataKey="cancelled" name="Cancelled" fill="#C62828" radius={[2, 2, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ReportCard>
          </Grid>

          <Grid item xs={12} lg={6}>
            <ReportCard title="Orders by Type">
              <ResponsiveContainer width="100%" height={240}>
                <PieChart>
                  <Pie data={orderTypeData} cx="50%" cy="50%" innerRadius={60} outerRadius={90} dataKey="value" paddingAngle={3}>
                    {orderTypeData.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend formatter={(v) => <span style={{ fontSize: 12 }}>{v}</span>} />
                </PieChart>
              </ResponsiveContainer>
            </ReportCard>
          </Grid>
        </Grid>
      </Box>
    </LocalizationProvider>
  )
}
