import { Box, Typography, Button, Paper } from '@mui/material'
import { Lock } from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'

export function PermissionDenied() {
  const navigate = useNavigate()

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', pt: { xs: 4, md: 8 } }}>
      <Paper variant="outlined" sx={{ p: 5, textAlign: 'center', maxWidth: 420 }}>
        <Box
          sx={{
            width: 64, height: 64, borderRadius: '50%', bgcolor: 'error.main', opacity: 0.12,
            display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 2,
          }}
        >
          <Lock sx={{ fontSize: 30, color: 'error.main' }} />
        </Box>
        <Typography variant="h6" fontWeight={700} gutterBottom>
          Permission Denied
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          You don't have access to this page. Contact your administrator if you believe this is a mistake.
        </Typography>
        <Button variant="contained" onClick={() => navigate('/dashboard')}>
          Back to Dashboard
        </Button>
      </Paper>
    </Box>
  )
}
