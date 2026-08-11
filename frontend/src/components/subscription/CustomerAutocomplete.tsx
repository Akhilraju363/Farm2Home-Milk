import { Autocomplete, TextField, CircularProgress, Box, Typography } from '@mui/material'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useDebounced } from '../../hooks/useDebounced'
import { customerService } from '../../services/customerService'
import type { Customer } from '../../types/customer.types'

const DEBOUNCE_MS = 400
const MIN_QUERY_LENGTH = 2

interface Props {
  value: Customer | null
  onChange: (customer: Customer | null) => void
  error?: boolean
  helperText?: string
}

/** Server-side, debounced customer search (GET /customers/search) - never loads the full
 *  customer base into the browser. Used by New Subscription so an admin can find who they're
 *  creating a subscription for without hardcoding or bulk-fetching customers. */
export function CustomerAutocomplete({ value, onChange, error, helperText }: Props) {
  const [inputValue, setInputValue] = useState('')
  const debouncedInput = useDebounced(inputValue, DEBOUNCE_MS)
  const searchReady = debouncedInput.trim().length >= MIN_QUERY_LENGTH

  const { data, isFetching } = useQuery({
    queryKey: ['customers', 'search', 'autocomplete', debouncedInput],
    queryFn: () => customerService.search(debouncedInput.trim()),
    enabled: searchReady,
  })
  const options = searchReady ? (data?.data.data.content ?? []) : []

  return (
    <Autocomplete
      value={value}
      onChange={(_e, newValue) => onChange(newValue)}
      inputValue={inputValue}
      onInputChange={(_e, newInputValue) => setInputValue(newInputValue)}
      options={options}
      loading={isFetching}
      getOptionLabel={(c) => `${c.firstName} ${c.lastName}`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      noOptionsText={searchReady ? 'No matching customers' : 'Type at least 2 characters to search'}
      renderOption={(props, c) => (
        <Box component="li" {...props} key={c.id}>
          <Box>
            <Typography variant="body2">{c.firstName} {c.lastName}</Typography>
            <Typography variant="caption" color="text.secondary">{c.mobile} • {c.customerCode}</Typography>
          </Box>
        </Box>
      )}
      renderInput={(params) => (
        <TextField
          {...params}
          label="Customer" required size="small" error={error} helperText={helperText}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {isFetching && <CircularProgress size={16} />}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
    />
  )
}
