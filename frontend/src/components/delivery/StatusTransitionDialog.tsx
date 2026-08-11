import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, CircularProgress, Alert } from '@mui/material'
import { useEffect, useState } from 'react'
import { ASSIGNMENT_STATUS_LABELS } from '../../types/delivery.types'
import type { AssignmentStatus } from '../../types/delivery.types'

interface Props {
  open: boolean
  targetStatus: AssignmentStatus | null
  loading?: boolean
  serverError?: string
  onClose: () => void
  onConfirm: (extra: { deliveryProof?: string; failureReason?: string }) => void
}

// DELIVERED requires a non-blank deliveryProof and FAILED requires a non-blank failureReason on
// the backend (DeliveryAssignmentServiceImpl.updateStatus) - both are enforced here too so the
// user gets an inline error instead of a round-trip 422. Neither field is validated/parsed as a
// real OTP or photo - it's a free-text string the backend just stores as-is.
export function StatusTransitionDialog({ open, targetStatus, loading, serverError, onClose, onConfirm }: Props) {
  const [proof, setProof] = useState('')
  const [reason, setReason] = useState('')
  const [fieldError, setFieldError] = useState('')

  useEffect(() => {
    if (open) { setProof(''); setReason(''); setFieldError('') }
  }, [open, targetStatus])

  if (!targetStatus) return null

  const needsProof = targetStatus === 'DELIVERED'
  const needsReason = targetStatus === 'FAILED'
  const label = ASSIGNMENT_STATUS_LABELS[targetStatus]

  const handleConfirm = () => {
    if (needsProof && !proof.trim()) { setFieldError('Delivery proof is required to mark as delivered.'); return }
    if (needsReason && !reason.trim()) { setFieldError('A failure reason is required.'); return }
    setFieldError('')
    onConfirm({
      deliveryProof: needsProof ? proof.trim() : undefined,
      failureReason: needsReason ? reason.trim() : undefined,
    })
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Mark as {label}?</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {serverError && <Alert severity="error">{serverError}</Alert>}
        {targetStatus === 'OUT_FOR_DELIVERY' && (
          <Alert severity="info" sx={{ fontSize: 13 }}>
            Requires the order to have at least a pending or successful payment on record.
          </Alert>
        )}
        {needsProof && (
          <TextField
            label="Delivery Proof" required fullWidth size="small" autoFocus
            placeholder="e.g. signed-by-customer, receipt reference"
            error={!!fieldError} helperText={fieldError || 'Free text - signature reference, receipt note, etc.'}
            value={proof} onChange={(e) => setProof(e.target.value)}
          />
        )}
        {needsReason && (
          <TextField
            label="Failure Reason" required fullWidth size="small" autoFocus multiline minRows={2}
            placeholder="e.g. Customer unavailable, wrong address"
            error={!!fieldError} helperText={fieldError}
            value={reason} onChange={(e) => setReason(e.target.value)}
          />
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={loading} color="inherit">Cancel</Button>
        <Button
          onClick={handleConfirm} disabled={loading} variant="contained"
          color={targetStatus === 'FAILED' ? 'error' : 'primary'}
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Confirm
        </Button>
      </DialogActions>
    </Dialog>
  )
}
