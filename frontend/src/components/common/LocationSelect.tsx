import { TextField, MenuItem, Typography } from '@mui/material'

export interface LocationSelectItem {
  id: string
  name: string
}

/** One cascading State/District/City dropdown. Values are always the backend's own location id -
 *  never a hardcoded string - and the three loading/empty/error states are shown explicitly
 *  rather than silently rendering an empty or fake-looking list. Extracted from RegisterPage's
 *  registration Address step (the original source of this pattern - no behavior change there)
 *  so the customer-facing delivery-address Edit flow can reuse the exact same cascading dropdown
 *  instead of a second implementation. */
export function LocationSelect({
  label, value, onChange, disabled, disabledReason, items, isLoading, isError, onRetry, showRequiredError,
  // Both default to RegisterPage's original look (its own call sites pass neither prop, so its
  // spacing/height is completely unaffected) - EditAddressDialog passes size="small" margin="none"
  // to match its own Address Line/Pincode fields and to let its own layout gap be the only
  // spacing between rows, rather than fighting this component's internal margin.
  size = 'medium', margin = 'normal',
}: {
  label: string
  value: string
  onChange: (id: string) => void
  disabled: boolean
  disabledReason?: string
  items: LocationSelectItem[]
  isLoading: boolean
  isError: boolean
  onRetry: () => void
  showRequiredError: boolean
  size?: 'small' | 'medium'
  margin?: 'none' | 'dense' | 'normal'
}) {
  const empty = !isLoading && !isError && items.length === 0
  const fieldDisabled = disabled || isLoading || isError || empty

  let helperText: React.ReactNode = ' '
  if (disabled) helperText = disabledReason
  else if (isLoading) helperText = `Loading ${label.toLowerCase()}s...`
  else if (isError) {
    helperText = (
      <>
        {`Unable to load ${label.toLowerCase()}s. `}
        <Typography component="span" variant="caption" color="primary.main" fontWeight={700}
          sx={{ cursor: 'pointer' }} onClick={onRetry}>
          Retry
        </Typography>
      </>
    )
  } else if (empty) helperText = `No ${label.toLowerCase()}s available`
  else if (showRequiredError) helperText = `Select a ${label.toLowerCase()}`

  return (
    <TextField
      select
      fullWidth
      margin={margin}
      size={size}
      label={label}
      value={fieldDisabled ? '' : value}
      onChange={(e) => onChange(e.target.value)}
      disabled={fieldDisabled}
      error={showRequiredError && !disabled && !isLoading}
      helperText={helperText}
      SelectProps={{ displayEmpty: true }}
    >
      {items.map((item) => (
        <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>
      ))}
    </TextField>
  )
}
