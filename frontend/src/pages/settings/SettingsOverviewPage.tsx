import { Box, Card, CardActionArea, CardContent, Grid, Typography } from '@mui/material'
import { Agriculture, ArrowForward, LocationCity } from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { useAuth } from '../../hooks/useAuth'

const cards = [
  { title: 'Farm & Business', description: 'Manage registered farms and business information.', path: '/settings/farm-business', icon: <Agriculture color="primary" sx={{ fontSize: 34 }} />, roles: ['SUPER_ADMIN', 'FARM_MANAGER', 'DELIVERY_MANAGER'] },
  // Location Master is SUPER_ADMIN/FARM_MANAGER only, matching LocationMasterController's own
  // @PreAuthorize - narrower than Farm & Business above, so DELIVERY_MANAGER (who can reach this
  // overview page) doesn't see a card that would 403 if clicked.
  { title: 'Location Master', description: 'Manage States, Districts and Cities.', path: '/settings/location-master', icon: <LocationCity color="primary" sx={{ fontSize: 34 }} />, roles: ['SUPER_ADMIN', 'FARM_MANAGER'] },
]

export function SettingsOverviewPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const visibleCards = cards.filter((card) => user?.roles.some((r) => card.roles.includes(r)) ?? false)
  return <Box><PageHeader title="Settings" subtitle="Manage your Farm2Home business configuration." />
    <Grid container spacing={2.5}>{visibleCards.map((card) => <Grid item xs={12} md={6} key={card.path}>
      <Card variant="outlined" sx={{ height: '100%', borderRadius: 2 }}><CardActionArea onClick={() => navigate(card.path)} sx={{ height: '100%' }}>
        <CardContent sx={{ minHeight: 170, display: 'flex', flexDirection: 'column', gap: 1.5 }}>{card.icon}<Typography variant="h6" fontWeight={700}>{card.title}</Typography><Typography color="text.secondary" variant="body2">{card.description}</Typography><Typography color="primary.main" fontWeight={700} variant="body2" sx={{ mt: 'auto' }}>Open {card.title} <ArrowForward sx={{ fontSize: 16, verticalAlign: 'text-bottom' }} /></Typography></CardContent>
      </CardActionArea></Card>
    </Grid>)}</Grid>
  </Box>
}
