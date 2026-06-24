import { Box, Typography, Button } from '@mui/material'
import type { ReactNode } from 'react'

interface Props {
  title: string
  subtitle?: string
  action?: { label: string; icon?: ReactNode; onClick: () => void }
}

export function PageHeader({ title, subtitle, action }: Props) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3 }}>
      <Box>
        <Typography variant="h5" fontWeight={700}>{title}</Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" mt={0.5}>{subtitle}</Typography>
        )}
      </Box>
      {action && (
        <Button variant="contained" startIcon={action.icon} onClick={action.onClick} sx={{ ml: 2 }}>
          {action.label}
        </Button>
      )}
    </Box>
  )
}
