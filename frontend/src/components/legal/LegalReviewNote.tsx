import { Alert } from '@mui/material'

/** Visually flags a block of legal copy that has not been reviewed/approved by counsel or the
 *  business - see DPDP_PROGRESS.md "Needs lawyer/business review" for the full list. Remove once
 *  the wrapped section is signed off. */
export function LegalReviewNote({ children }: { children: React.ReactNode }) {
  return (
    <Alert severity="warning" variant="outlined" sx={{ my: 1.5 }}>
      {children}
    </Alert>
  )
}
