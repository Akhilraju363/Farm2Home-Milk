import {
  Grid, Box, Card, CardContent, Typography, Chip, Button, LinearProgress,
} from '@mui/material'
import {
  AttachMoney, ShoppingCart, CalendarMonth, People, Subscriptions,
  AddBox, ChevronRight,
} from '@mui/icons-material'
import {
  ResponsiveContainer, LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import { useQuery, useQueries } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { StatCard } from '../../components/common/StatCard'
import { useAuth } from '../../hooks/useAuth'
import { dashboardService } from '../../services/dashboardService'
import { orderService } from '../../services/orderService'
import { customerService } from '../../services/customerService'
import { formatCurrency, statusColor } from '../../utils/formatters'

const WEEKDAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

function ChartCard({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 2.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="subtitle1" fontWeight={600}>{title}</Typography>
        {action}
      </Box>
      {children}
    </Card>
  )
}

function greeting() {
  const hour = dayjs().hour()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

export function DashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const today = dayjs().format('YYYY-MM-DD')
  const weekAgo = dayjs().subtract(6, 'day').format('YYYY-MM-DD')

  const { data: summaryRes } = useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: () => dashboardService.getSummary(),
  })
  const summary = summaryRes?.data.data

  const { data: revenueTrendRes } = useQuery({
    queryKey: ['dashboard', 'revenue-trend', weekAgo, today],
    queryFn: () => dashboardService.getRevenueTrend(weekAgo, today),
  })
  const revenueChartData = (revenueTrendRes?.data.data.points ?? []).map((p) => ({
    date: dayjs(p.period).format('DD MMM'),
    revenue: p.revenue,
  }))

  const { data: customerGrowthRes } = useQuery({
    queryKey: ['dashboard', 'customer-growth', weekAgo, today],
    queryFn: () => dashboardService.getCustomerGrowthTrend(weekAgo, today),
  })
  const newCustomersThisWeek = (customerGrowthRes?.data.data.points ?? [])
    .reduce((sum, p) => sum + p.newCustomers, 0)

  const { data: subscriptionTrendRes } = useQuery({
    queryKey: ['dashboard', 'subscription-trend', weekAgo, today],
    queryFn: () => dashboardService.getSubscriptionTrend(weekAgo, today),
  })
  const subscriptionChartData = WEEKDAY_LABELS.map((label, i) => {
    const point = (subscriptionTrendRes?.data.data.points ?? [])
      .find((p) => dayjs(p.period).day() === (i + 1) % 7)
    return { day: label, count: point?.newSubscriptions ?? 0 }
  })

  const { data: recentOrdersRes } = useQuery({
    queryKey: ['dashboard', 'recent-orders'],
    queryFn: () => orderService.getRecent(5),
  })
  const recentOrders = recentOrdersRes?.data.data.content ?? []

  const customerQueries = useQueries({
    queries: recentOrders.map((order) => ({
      queryKey: ['customer', order.customerId],
      queryFn: () => customerService.getById(order.customerId),
      enabled: !!order.customerId,
      staleTime: 5 * 60 * 1000,
    })),
  })
  const customerNameById = new Map<string, string>()
  recentOrders.forEach((order, i) => {
    // customerService.getById resolves to ApiResponse<Customer> (matching every other endpoint's
    // envelope in this backend) - an extra .data hop is needed to reach the Customer itself.
    const c = customerQueries[i]?.data?.data.data
    if (c) customerNameById.set(order.customerId, `${c.firstName} ${c.lastName}`)
  })

  return (
    <Box>
      {/* Welcome header */}
      <Box mb={3}>
        <Typography variant="h5" fontWeight={700}>
          {greeting()}, {user?.username ?? 'Admin'}.
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Here's what's happening on your farm today.
        </Typography>
      </Box>

      {/* KPI cards */}
      <Grid container spacing={2} mb={3}>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <StatCard
            title="Today's Revenue"
            value={formatCurrency(summary?.revenueToday ?? 0)}
            icon={<AttachMoney />}
            color="#2E7D32"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <StatCard
            title="Today's Orders"
            value={summary?.todaysOrders ?? 0}
            icon={<ShoppingCart />}
            color="#1565C0"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <StatCard
            title="Monthly Revenue"
            value={formatCurrency(summary?.revenueThisMonth ?? 0)}
            icon={<CalendarMonth />}
            color="#0277BD"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <StatCard
            title="Total Customers"
            value={(summary?.totalCustomers ?? 0).toLocaleString()}
            icon={<People />}
            color="#7B1FA2"
            subtitle={`+${newCustomersThisWeek} this week`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <StatCard
            title="Active Subs"
            value={(summary?.activeSubscriptions ?? 0).toLocaleString()}
            icon={<Subscriptions />}
            color="#F57F17"
          />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        {/* Main column */}
        <Grid item xs={12} lg={8}>
          <Box mb={2}>
            <ChartCard title="Revenue Trend (Last 7 Days)">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={revenueChartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(v: number) => [formatCurrency(v), 'Revenue']} />
                  <Line
                    type="monotone" dataKey="revenue" stroke="#2E7D32"
                    strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>
          </Box>

          <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3 }}>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant="subtitle1" fontWeight={600}>Recent Orders</Typography>
                <Button size="small" onClick={() => navigate('/orders')}>View All</Button>
              </Box>
              {recentOrders.length === 0 ? (
                <Typography color="text.secondary" variant="body2" textAlign="center" py={3}>
                  No recent orders
                </Typography>
              ) : (
                recentOrders.map((order) => (
                  <Box
                    key={order.id}
                    sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1.25, borderBottom: '1px solid', borderColor: 'divider' }}
                  >
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={600} color="primary.main">{order.orderNumber}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {customerNameById.get(order.customerId) ?? '—'}
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <Typography variant="body2" fontWeight={500}>
                        {formatCurrency(order.totalAmount)}
                      </Typography>
                      <Chip
                        label={order.status.replace(/_/g, ' ')}
                        size="small"
                        color={statusColor(order.status)}
                        sx={{ fontWeight: 600, fontSize: 11 }}
                      />
                    </Box>
                  </Box>
                ))
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Right sidebar */}
        <Grid item xs={12} lg={4}>
          <Box mb={2}>
            <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 2.5 }}>
              <Typography variant="subtitle1" fontWeight={600} mb={2}>Quick Actions</Typography>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => navigate('/inventory')}
                sx={{ justifyContent: 'space-between', textTransform: 'none', py: 1.5, px: 2 }}
                endIcon={<ChevronRight />}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <AddBox color="primary" />
                  <Box sx={{ textAlign: 'left' }}>
                    <Typography variant="body2" fontWeight={600} color="text.primary">Add Product</Typography>
                    <Typography variant="caption" color="text.secondary">Catalog entry</Typography>
                  </Box>
                </Box>
              </Button>
            </Card>
          </Box>

          <Box mb={2}>
            <ChartCard title="Subscription Analytics (Last 7 Days)">
              <ResponsiveContainer width="100%" height={140}>
                <BarChart data={subscriptionChartData}>
                  <XAxis dataKey="day" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                  <Tooltip />
                  <Bar dataKey="count" fill="#2E7D32" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </Box>

          <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 2.5 }}>
            <Typography variant="subtitle1" fontWeight={600} mb={1}>Milk Production Today</Typography>
            <Typography variant="h5" fontWeight={700} color="primary.main">
              {(summary?.milkProductionToday ?? 0).toFixed(1)} L
            </Typography>
            {summary && summary.lowStockProductsCount > 0 && (
              <Box mt={2}>
                <Typography variant="caption" color="error.main">
                  {summary.lowStockProductsCount} product{summary.lowStockProductsCount === 1 ? '' : 's'} low on stock
                </Typography>
                <LinearProgress variant="determinate" value={100} color="error" sx={{ mt: 0.5, height: 4, borderRadius: 2 }} />
              </Box>
            )}
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
