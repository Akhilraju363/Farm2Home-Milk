import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, CircularProgress, Alert } from '@mui/material'
import { useEffect, useState } from 'react'

interface Props {
  open: boolean
  loading?: boolean
  serverError?: string
  onClose: () => void
  onConfirm: (reason: string) => void
}

// POST /{id}/delay is notification-only on the backend - it does not persist any field on the
// assignment (markDelayed is a readOnly transaction), it just sends a "running late" signal.
export function DelayDialog({ open, loading, serverError, onClose, onConfirm }: Props) {
  const [reason, setReason] = useState('')
  const [fieldError, setFieldError] = useState('')

  useEffect(() => {
    if (open) { setReason(''); setFieldError('') }
  }, [open])

  const handleConfirm = () => {
    if (!reason.trim()) { setFieldError('A reason is required.'); return }
    setFieldError('')
    onConfirm(reason.trim())
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Notify Delay</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {serverError && <Alert severity="error">{serverError}</Alert>}
        <Alert severity="info" sx={{ fontSize: 13 }}>
          Sends a delay notification only - the delivery's status is unchanged.
        </Alert>
        <TextField
          label="Reason" required fullWidth size="small" autoFocus multiline minRows={2}
          placeholder="e.g. Heavy traffic on route"
          error={!!fieldError} helperText={fieldError}
          value={reason} onChange={(e) => setReason(e.target.value)}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={loading} color="inherit">Cancel</Button>
        <Button
          onClick={handleConfirm} disabled={loading} variant="contained" color="warning"
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Send Notification
        </Button>
      </DialogActions>
    </Dialog>
  )
}
