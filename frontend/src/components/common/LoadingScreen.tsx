import { Box, LinearProgress, Typography } from '@mui/material'
import YardIcon from '@mui/icons-material/Yard'
import LightModeIcon from '@mui/icons-material/LightMode'

export function LoadingScreen() {
  return (
    <Box
      sx={{
        position: 'fixed',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(circle at 30% 20%, #ffffff 0%, #dcedc8 45%, #a5d6a7 100%)',
        zIndex: 9999,
      }}
    >
      <Box
        sx={{
          width: 96,
          height: 96,
          borderRadius: 3,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'primary.dark',
          boxShadow: 4,
          mb: 3,
        }}
      >
        <YardIcon sx={{ fontSize: 48, color: '#e8f5e9' }} />
      </Box>

      <Typography variant="h4" fontWeight={700} color="primary.dark">
        Farm2Home
      </Typography>
      <Typography
        variant="caption"
        sx={{ letterSpacing: 2, color: 'text.secondary', mt: 0.5 }}
      >
        PURE. FRESH. TO YOUR DOORSTEP.
      </Typography>

      <Box sx={{ width: 160, borderTop: '2px solid', borderColor: 'primary.main', my: 3 }} />

      <Box sx={{ width: 220 }}>
        <LinearProgress color="primary" />
      </Box>
      <Typography
        variant="overline"
        sx={{ letterSpacing: 1.5, color: 'text.secondary', mt: 1 }}
      >
        Harvesting excellence...
      </Typography>

      <Typography
        variant="caption"
        sx={{ position: 'absolute', bottom: 16, color: 'text.disabled' }}
      >
        F2H Digital Ecosystem &copy; {new Date().getFullYear()}
      </Typography>

      <LightModeIcon
        sx={{ position: 'absolute', bottom: 16, right: 16, fontSize: 18, color: 'text.disabled' }}
      />
    </Box>
  )
}
