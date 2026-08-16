import { Box, Paper, Typography, Button, Stack } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { hasDecidedTrackerConsent, setTrackerConsent } from '../../utils/trackerConsent'
import { useAuth } from '../../hooks/useAuth'
import { consentService } from '../../services/consentService'

/** Global, once-per-browser banner gating the ANALYTICS_COOKIES purpose - see trackerConsent.ts
 *  for why this exists even though no tracker is wired in yet. Mounted once in App.tsx so it
 *  shows regardless of which page the visitor lands on, logged in or not. */
export function ConsentBanner() {
  const { isAuthenticated } = useAuth()
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    setVisible(!hasDecidedTrackerConsent())
  }, [])

  const decide = (granted: boolean) => {
    setTrackerConsent(granted)
    setVisible(false)
    if (isAuthenticated) {
      consentService.setOne('ANALYTICS_COOKIES', granted).catch(() => {
        // Best-effort - the local decision (which is what actually gates loadTrackerScript)
        // already took effect regardless of whether the server-side record saved.
      })
    }
  }

  if (!visible) return null

  return (
    <Paper
      elevation={6}
      role="dialog"
      aria-label="Cookie and tracking consent"
      sx={{
        position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: (theme) => theme.zIndex.snackbar,
        p: 2, display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2,
        borderRadius: 0, borderTop: '1px solid', borderColor: 'divider',
      }}
    >
      <Box sx={{ maxWidth: 640 }}>
        <Typography variant="body2">
          Farm2Home does not currently use any analytics, advertising, or marketing trackers - the
          essential cookies/local storage we use are only what's needed to keep you signed in. If
          we ever add optional analytics, this choice is what decides whether they run. See our{' '}
          <Typography component={RouterLink} to="/privacy" variant="body2" color="primary.main" sx={{ textDecoration: 'none' }}>
            Privacy Notice
          </Typography>.
        </Typography>
      </Box>
      <Stack direction="row" spacing={1}>
        <Button size="small" variant="outlined" onClick={() => decide(false)}>Reject Non-Essential</Button>
        <Button size="small" variant="contained" onClick={() => decide(true)}>Accept</Button>
      </Stack>
    </Paper>
  )
}
