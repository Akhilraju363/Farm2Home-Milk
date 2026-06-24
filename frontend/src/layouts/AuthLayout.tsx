import { Box, Paper, Typography } from '@mui/material'
import { Agriculture } from '@mui/icons-material'
import { Outlet } from 'react-router-dom'

export function AuthLayout() {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'primary.dark',
        backgroundImage: 'linear-gradient(135deg, #1B5E20 0%, #2E7D32 50%, #388E3C 100%)',
        p: 2,
      }}
    >
      <Paper
        elevation={8}
        sx={{ width: '100%', maxWidth: 420, p: 4, borderRadius: 3 }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 4 }}>
          <Agriculture sx={{ fontSize: 40, color: 'primary.main' }} />
          <Box>
            <Typography variant="h5" fontWeight={700} color="primary.main" lineHeight={1.2}>
              Farm2Home Milk
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Farm to your doorstep, fresh every day
            </Typography>
          </Box>
        </Box>
        <Outlet />
      </Paper>
    </Box>
  )
}
