import { Card, CardContent, Typography, Box, type SxProps } from '@mui/material'
import type { ReactNode } from 'react'

interface Props {
  title: string
  value: string | number
  subtitle?: string
  icon: ReactNode
  color?: string
  sx?: SxProps
}

export function StatCard({ title, value, subtitle, icon, color = '#2E7D32', sx }: Props) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, height: '100%', display: 'flex', ...sx }}>
      <CardContent sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 2, p: 2.5, '&:last-child': { pb: 2.5 } }}>
        <Box
          sx={{
            width: 52, height: 52, borderRadius: 2,
            bgcolor: `${color}18`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color, fontSize: 26, flexShrink: 0,
          }}
        >
          {icon}
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" color="text.secondary" noWrap>{title}</Typography>
          <Typography variant="h5" fontWeight={700} lineHeight={1.3}>{value}</Typography>
          {subtitle && (
            <Typography variant="caption" color="text.secondary">{subtitle}</Typography>
          )}
        </Box>
      </CardContent>
    </Card>
  )
}
