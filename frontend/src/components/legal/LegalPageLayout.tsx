import { Box, Container, Paper, Typography, IconButton } from '@mui/material'
import { Agriculture, ArrowBack } from '@mui/icons-material'
import { Link as RouterLink } from 'react-router-dom'
import { PublicFooter } from '../layout/PublicFooter'

interface Props {
  title: string
  lastUpdated?: string
  children: React.ReactNode
}

/** Standalone page shell (not MainLayout) - reachable whether or not the visitor is logged in,
 *  same rationale as LoginPage/RegisterPage sitting outside MainLayout. Used by the Privacy
 *  Notice, Terms, and Data Rights Request pages. */
export function LegalPageLayout({ title, lastUpdated, children }: Props) {
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: { xs: 2, md: 4 }, py: 2 }}>
        <IconButton component={RouterLink} to="/" aria-label="Back">
          <ArrowBack />
        </IconButton>
        <Agriculture color="success" />
        <Typography variant="subtitle1" fontWeight={700}>Farm2Home Milk</Typography>
      </Box>

      <Container maxWidth="md" sx={{ flex: 1, pb: 4 }}>
        <Paper variant="outlined" sx={{ p: { xs: 2.5, md: 4 }, borderRadius: 2 }}>
          <Typography variant="h4" fontWeight={700} gutterBottom>{title}</Typography>
          {lastUpdated && (
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 3 }}>
              Last updated: {lastUpdated}
            </Typography>
          )}
          {children}
        </Paper>
      </Container>

      <Box sx={{ px: { xs: 2, md: 4 } }}>
        <PublicFooter />
      </Box>
    </Box>
  )
}
