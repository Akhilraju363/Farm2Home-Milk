import { Box, Typography, Stack, Divider } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { GRIEVANCE_OFFICER } from '../../constants/legal'

interface Props {
  // 'compact' fits inside the existing single-line copyright strips on LoginPage/RegisterPage;
  // 'full' is the standalone footer added to MainLayout and the legal pages.
  variant?: 'compact' | 'full'
}

/** The only site-wide place the Grievance Officer contact is published, other than the Privacy
 *  Notice page itself (DPDP Act 2023 s.13 requires this to be readily available, not buried). */
export function PublicFooter({ variant = 'full' }: Props) {
  const year = new Date().getFullYear()

  const links = (
    <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap justifyContent="center">
      <Typography component={RouterLink} to="/privacy" variant="caption" color="text.secondary" sx={{ textDecoration: 'none' }}>
        Privacy Notice
      </Typography>
      <Typography component={RouterLink} to="/terms" variant="caption" color="text.secondary" sx={{ textDecoration: 'none' }}>
        Terms & Conditions
      </Typography>
      <Typography component={RouterLink} to="/data-rights-request" variant="caption" color="text.secondary" sx={{ textDecoration: 'none' }}>
        Data Rights / Grievance
      </Typography>
      <Typography
        component="a"
        href={`mailto:${GRIEVANCE_OFFICER.email}`}
        variant="caption"
        color="text.secondary"
        sx={{ textDecoration: 'none' }}
      >
        Grievance Officer: {GRIEVANCE_OFFICER.email}
      </Typography>
    </Stack>
  )

  if (variant === 'compact') {
    return (
      <Box sx={{ mt: 1.5, textAlign: 'center' }}>
        {links}
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
          © {year} Farm2Home Milk. All rights reserved.
        </Typography>
      </Box>
    )
  }

  return (
    <Box component="footer" sx={{ mt: 4, pt: 3, pb: 2, borderTop: '1px solid', borderColor: 'divider' }}>
      <Stack spacing={1.5} alignItems="center">
        {links}
        <Divider flexItem sx={{ maxWidth: 200 }} />
        <Typography variant="caption" color="text.secondary">
          © {year} Farm2Home Milk. All rights reserved.
        </Typography>
      </Stack>
    </Box>
  )
}
