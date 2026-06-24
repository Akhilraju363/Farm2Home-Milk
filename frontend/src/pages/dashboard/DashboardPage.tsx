import { Grid, Box, Card, CardContent, Typography, Chip } from '@mui/material'
import { People, Subscriptions, WaterDrop, Inventory } from '@mui/icons-material'
import {
  ResponsiveContainer, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, PieChart, Pie, Cell,
} from 'recharts'
import { useQuery } from '@tanstack/react-query'
import { StatCard } from '../../components/common/StatCard'
import { customerService } from '../../services/customerService'
import { subscriptionService } from '../../services/subscriptionService'
import { productionService } from '../../services/productionService'
import { inventoryService } from '../../services/inventoryService'
import { orderService } from '../../services/orderService'
import { formatDate } from '../../utils/formatters'
import dayjs from 'dayjs'

const ORDER_STATUS_COLORS: Record<string, string> = {
  PENDING: '#F9A825', ASSIGNED: '#1565C0', OUT_FOR_DELIVERY: '#0277BD',
  DELIVERED: '#2E7D32', CANCELLED: '#C62828',
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
      <Typography variant="subtitle1" fontWeight={600} mb={2}>{title}</Typography>
      {children}
    </Card>
  )
}

export function DashboardPage() {
  const from = dayjs().subtract(13, 'day').format('YYYY-MM-DD')
  const to   = dayjs().format('YYYY-MM-DD')

  const { data: customers } = useQuery({
    queryKey: ['customers', 'count'],
    queryFn: () => customerService.getAll(0, 1),
  })

  const { data: activeSubs } = useQuery({
    queryKey: ['subscriptions', 'active'],
    queryFn: () => subscriptionService.getAll({ status: 'ACTIVE', size: 1 }),
  })

  const { data: productionSummary } = useQuery({
    queryKey: ['production', 'summary', from, to],
    queryFn: () => productionService.getDailySummary(from, to),
  })

  const { data: lowStock } = useQuery({
    queryKey: ['inventory', 'low-stock'],
    queryFn: () => inventoryService.getLowStock(),
  })

  const { data: recentOrders } = useQuery({
    queryKey: ['orders', 'recent'],
    queryFn: () => orderService.getAll({ size: 100 }),
  })

  const prodArr = productionSummary?.data ?? []
  const todayProduction = prodArr.length > 0 ? prodArr[prodArr.length - 1].totalLiters : 0
  const totalCustomers = customers?.data?.totalElements ?? 0
  const activeSubsCount = activeSubs?.data?.totalElements ?? 0
  const lowStockCount = lowStock?.data?.length ?? 0

  // Build production chart data
  const productionChartData = (productionSummary?.data ?? []).map((d) => ({
    date: dayjs(d.date).format('DD MMM'),
    liters: Number(d.totalLiters.toFixed(1)),
  }))

  // Order status distribution
  const orderStatusMap: Record<string, number> = {}
  for (const order of recentOrders?.data?.content ?? []) {
    orderStatusMap[order.status] = (orderStatusMap[order.status] ?? 0) + 1
  }
  const orderPieData = Object.entries(orderStatusMap).map(([name, value]) => ({ name, value }))

  return (
    <Box>
      {/* Stat cards */}
      <Grid container spacing={2} mb={3}>
        <Grid item xs={12} sm={6} lg={3}>
          <StatCard
            title="Total Customers"
            value={totalCustomers.toLocaleString()}
            icon={<People />}
            color="#2E7D32"
            subtitle="All registered customers"
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <StatCard
            title="Active Subscriptions"
            value={activeSubsCount.toLocaleString()}
            icon={<Subscriptions />}
            color="#1565C0"
            subtitle="Currently active"
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <StatCard
            title="Today's Production"
            value={`${todayProduction.toFixed(1)} L`}
            icon={<WaterDrop />}
            color="#0277BD"
            subtitle={formatDate(to)}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <StatCard
            title="Low Stock Alerts"
            value={lowStockCount}
            icon={<Inventory />}
            color={lowStockCount > 0 ? '#C62828' : '#2E7D32'}
            subtitle="Items below reorder level"
          />
        </Grid>
      </Grid>

      {/* Charts row */}
      <Grid container spacing={2} mb={3}>
        <Grid item xs={12} lg={8}>
          <ChartCard title="Milk Production (Last 14 Days)">
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={productionChartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} unit=" L" />
                <Tooltip formatter={(v: number) => [`${v} L`, 'Production']} />
                <Line
                  type="monotone" dataKey="liters" stroke="#2E7D32"
                  strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={4}>
          <ChartCard title="Orders by Status">
            {orderPieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie
                    data={orderPieData} cx="50%" cy="50%"
                    innerRadius={60} outerRadius={90}
                    paddingAngle={3} dataKey="value"
                  >
                    {orderPieData.map((entry) => (
                      <Cell key={entry.name} fill={ORDER_STATUS_COLORS[entry.name] ?? '#999'} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend formatter={(v) => <span style={{ fontSize: 12 }}>{v}</span>} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <Box sx={{ height: 260, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Typography color="text.secondary" variant="body2">No order data</Typography>
              </Box>
            )}
          </ChartCard>
        </Grid>
      </Grid>

      {/* Recent orders summary */}
      <Grid container spacing={2}>
        <Grid item xs={12}>
          <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
            <CardContent>
              <Typography variant="subtitle1" fontWeight={600} mb={2}>
                Recent Orders
              </Typography>
              {(recentOrders?.data?.content ?? []).slice(0, 8).map((order) => (
                <Box
                  key={order.id}
                  sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1, borderBottom: '1px solid', borderColor: 'divider' }}
                >
                  <Box>
                    <Typography variant="body2" fontWeight={600}>{order.orderNumber}</Typography>
                    <Typography variant="caption" color="text.secondary">{formatDate(order.orderDate)}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Typography variant="body2" fontWeight={500}>
                      ₹{Number(order.totalAmount).toFixed(2)}
                    </Typography>
                    <Chip
                      label={order.status}
                      size="small"
                      sx={{
                        bgcolor: `${ORDER_STATUS_COLORS[order.status]}22`,
                        color: ORDER_STATUS_COLORS[order.status],
                        fontWeight: 600, fontSize: 11,
                      }}
                    />
                  </Box>
                </Box>
              ))}
              {(recentOrders?.data?.content?.length ?? 0) === 0 && (
                <Typography color="text.secondary" variant="body2" textAlign="center" py={3}>
                  No recent orders
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
