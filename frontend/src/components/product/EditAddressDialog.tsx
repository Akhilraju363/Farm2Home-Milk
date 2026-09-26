import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, CircularProgress,
  Alert, Typography, Checkbox, FormControlLabel,
} from '@mui/material'
import { Close, MyLocation, CheckCircle, LocationOff } from '@mui/icons-material'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useQuery } from '@tanstack/react-query'
import { customerService } from '../../services/customerService'
import { indiaLocationService } from '../../services/indiaLocationService'
import { useGeolocationCapture } from '../../hooks/useGeolocationCapture'
import { LocationSelect } from '../common/LocationSelect'
import type { CustomerAddress, UpdateAddressRequest } from '../../types/customer.types'

const schema = yup.object({
  addressLine1: yup.string().trim().required('Address line 1 is required').max(255, 'Max 255 characters'),
  addressLine2: yup.string().max(255, 'Max 255 characters'),
  pincode: yup.string().trim().required('Pincode is required').matches(/^\d{6}$/, 'Enter a valid 6-digit pincode'),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  customerId: string
  address: CustomerAddress | null
  onClose: () => void
  /** Fires after a successful save (and, if the customer also checked "Make this my default
   *  address", after that follow-up call too) - the caller re-fetches the address list and
   *  delivery availability the same way it already does after Add/Set Default. */
  onSaved: () => void
}

/** Self-service address edit, reached from the Shop/Checkout "Change Address" dialog's Edit
 *  action. Reuses the exact same PUT /{customerId}/addresses/{addressId} endpoint the admin
 *  Customer Management screen's AddressFormDialog already calls via customerService.updateAddress
 *  - no new/duplicate address API. Unlike that admin dialog (and unlike this same file's sibling
 *  DeliveryAddressDialog's own "Add New Address" form), this one also collects District via the
 *  same database-backed State -> District -> City cascade RegisterPage's registration Address
 *  step uses (see LocationSelect) - the customer's own address deserves the same location-master
 *  accuracy their original registration used, and it lets this form resolve/display the address's
 *  existing District. */
export function EditAddressDialog({ open, customerId, address, onClose, onSaved }: Props) {
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')
  const [makeDefault, setMakeDefault] = useState(false)
  const [locationTouched, setLocationTouched] = useState(false)

  const [stateId, setStateId] = useState('')
  const [districtId, setDistrictId] = useState('')
  const [cityId, setCityId] = useState('')
  // Tracks whether this edit session has already attempted to resolve the address's saved
  // state/district/city NAME strings to the cascade's own ids, so that arrival of paginated/
  // refetched location data later doesn't re-run initialization and clobber a change the customer
  // has since made themselves. Reset only when the dialog opens for a (possibly different) address.
  const resolvedRef = useRef({ state: false, district: false, city: false })

  const { coords, locating, error: locationError, capture, reset: resetLocation } = useGeolocationCapture()

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { addressLine1: '', addressLine2: '', pincode: '' },
  })

  const statesQuery = useQuery({
    queryKey: ['indiaLocations', 'states'],
    queryFn: () => indiaLocationService.getStates(),
    enabled: open,
  })
  const states = useMemo(() => statesQuery.data?.data.data ?? [], [statesQuery.data])

  const districtsQuery = useQuery({
    queryKey: ['indiaLocations', 'districts', stateId],
    queryFn: () => indiaLocationService.getDistricts(stateId),
    enabled: open && Boolean(stateId),
  })
  const districts = useMemo(() => districtsQuery.data?.data.data ?? [], [districtsQuery.data])

  const citiesQuery = useQuery({
    queryKey: ['indiaLocations', 'cities', districtId],
    queryFn: () => indiaLocationService.getCities(districtId),
    enabled: open && Boolean(districtId),
  })
  const cities = useMemo(() => citiesQuery.data?.data.data ?? [], [citiesQuery.data])

  // Reset everything for a fresh edit session whenever the dialog opens (or opens for a
  // different address) - form fields, the cascade's own selections, and the "already resolved"
  // guards below, so a previous address's leftover state/district/city ids never briefly flash
  // for the next one.
  useEffect(() => {
    if (!open || !address) return
    setServerError('')
    setLocationTouched(false)
    setMakeDefault(address.defaultAddress)
    resetLocation()
    reset({
      addressLine1: address.addressLine1,
      addressLine2: address.addressLine2 ?? '',
      pincode: address.pincode,
    })
    setStateId('')
    setDistrictId('')
    setCityId('')
    resolvedRef.current = { state: false, district: false, city: false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, address?.id])

  // Resolve State -> District -> City ids from the address's saved NAME strings, one level at a
  // time as each level's own list arrives - never overwrites a selection the customer has since
  // made themselves (guarded by resolvedRef, not by re-running on every render).
  useEffect(() => {
    if (!open || !address || resolvedRef.current.state || states.length === 0) return
    const match = states.find((s) => s.name === address.state)
    if (match) setStateId(match.id)
    resolvedRef.current.state = true
  }, [open, address, states])

  useEffect(() => {
    if (!open || !address || !stateId || resolvedRef.current.district || districts.length === 0) return
    const match = address.district ? districts.find((d) => d.name === address.district) : undefined
    if (match) setDistrictId(match.id)
    resolvedRef.current.district = true
  }, [open, address, stateId, districts])

  useEffect(() => {
    if (!open || !address || !districtId || resolvedRef.current.city || cities.length === 0) return
    const match = cities.find((c) => c.name === address.city)
    if (match) setCityId(match.id)
    resolvedRef.current.city = true
  }, [open, address, districtId, cities])

  // User-driven changes (distinct from the resolution effects above, which must NOT cascade-clear
  // while restoring the address's existing values) - matches RegisterPage's own Address step.
  const handleStateChange = (id: string) => {
    setStateId(id)
    setDistrictId('')
    setCityId('')
  }
  const handleDistrictChange = (id: string) => {
    setDistrictId(id)
    setCityId('')
  }

  const locationComplete = Boolean(stateId && districtId && cityId)
  const hasCoordinates = address?.latitude != null && address?.longitude != null

  const closeForm = () => { if (!submitting) onClose() }

  const onSubmit = async (values: FormValues) => {
    setLocationTouched(true)
    if (!address || !locationComplete) return
    const state = states.find((s) => s.id === stateId)
    const district = districts.find((d) => d.id === districtId)
    const city = cities.find((c) => c.id === cityId)
    if (!state || !district || !city) return

    const addressLine1 = values.addressLine1.trim()
    const addressLine2 = values.addressLine2?.trim() || undefined
    const pincode = values.pincode.trim()
    const locationChanged =
      addressLine1 !== address.addressLine1
      || (addressLine2 ?? '') !== (address.addressLine2 ?? '')
      || pincode !== address.pincode
      || state.name !== address.state
      || district.name !== (address.district ?? '')
      || city.name !== address.city

    const payload: UpdateAddressRequest = {
      addressLine1, addressLine2, pincode,
      city: city.name, state: state.name, district: district.name,
    }
    if (coords) {
      // Freshly captured this session - always wins, regardless of whether the address text
      // itself also changed.
      payload.latitude = coords.latitude
      payload.longitude = coords.longitude
    } else if (locationChanged) {
      // Physical address changed but nothing was re-captured - do not silently keep the old
      // (now possibly wrong) coordinates against a different address.
      payload.clearCoordinates = true
    }
    // else: nothing address-related changed and nothing was re-captured - omit lat/lng/
    // clearCoordinates entirely so the existing coordinates are left exactly as they were.

    setServerError('')
    setSubmitting(true)
    try {
      await customerService.updateAddress(customerId, address.id, payload)
      if (makeDefault && !address.defaultAddress) {
        await customerService.setDefaultAddress(customerId, address.id)
      }
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Could not save this address. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : closeForm} maxWidth="xs" fullWidth>
      <DialogTitle fontWeight={700}>Edit Delivery Address</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* One consistent vertical rhythm (gap: 2 = 16px) for every row in the form - each row
            below is a single flex/grid item and none of them carry their own ad hoc margin, so
            this gap is the only thing that ever spaces them apart. */}
        <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          <TextField
            label="Address Line 1" required fullWidth size="small"
            helperText={errors.addressLine1?.message ?? 'House/Flat No. and street'}
            error={!!errors.addressLine1}
            {...register('addressLine1')}
          />
          <TextField
            label="Address Line 2" fullWidth size="small"
            helperText={errors.addressLine2?.message ?? 'Area/Locality (optional)'}
            error={!!errors.addressLine2}
            {...register('addressLine2')}
          />

          {/* State/District and City/Pincode: equal-width two-column grid on desktop, a single
              column on mobile (xs) - both rows share the exact same column template so they stay
              aligned with each other, and every field inside is size="small" margin="none" so
              all four controls render at the same height as Address Line/Pincode above. */}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
            <LocationSelect
              label="State"
              value={stateId}
              onChange={handleStateChange}
              disabled={false}
              items={states}
              isLoading={statesQuery.isLoading}
              isError={statesQuery.isError}
              onRetry={() => statesQuery.refetch()}
              showRequiredError={locationTouched && !stateId}
              size="small"
              margin="none"
            />
            <LocationSelect
              label="District"
              value={districtId}
              onChange={handleDistrictChange}
              disabled={!stateId}
              disabledReason="Select a state first"
              items={districts}
              isLoading={districtsQuery.isLoading}
              isError={districtsQuery.isError}
              onRetry={() => districtsQuery.refetch()}
              showRequiredError={locationTouched && Boolean(stateId) && !districtId}
              size="small"
              margin="none"
            />
          </Box>

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
            <LocationSelect
              label="City"
              value={cityId}
              onChange={setCityId}
              disabled={!districtId}
              disabledReason="Select a district first"
              items={cities}
              isLoading={citiesQuery.isLoading}
              isError={citiesQuery.isError}
              onRetry={() => citiesQuery.refetch()}
              showRequiredError={locationTouched && Boolean(districtId) && !cityId}
              size="small"
              margin="none"
            />
            <TextField
              label="Pincode" required fullWidth size="small"
              error={!!errors.pincode} helperText={errors.pincode?.message}
              {...register('pincode')}
            />
          </Box>

          {/* Location section - one compact cluster, its own small internal gap, no margin of
              its own; the form's gap above/below is what separates it from City/Pincode and the
              default-address section. */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {coords ? (
              <Alert severity="success" icon={<CheckCircle fontSize="small" />}>
                New location captured - this will replace the saved one.
              </Alert>
            ) : (
              <>
                <Button
                  onClick={capture} disabled={locating} size="small" variant="outlined"
                  startIcon={locating ? <CircularProgress size={16} /> : <MyLocation />}
                  sx={{ alignSelf: 'flex-start' }}
                >
                  {locating ? 'Locating…' : 'Use my current location'}
                </Button>
                <Typography variant="caption" color="text.secondary">
                  {hasCoordinates
                    ? 'Current saved location: Location set. Re-capture it if you changed the address above.'
                    : 'Location not set for this address.'}
                </Typography>
              </>
            )}
            {locationError && <Alert severity="warning">{locationError}</Alert>}
            {!coords && !hasCoordinates && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <LocationOff sx={{ fontSize: 14 }} color="disabled" />
                <Typography variant="caption" color="text.secondary">Location not set</Typography>
              </Box>
            )}
          </Box>

          {/* Default-address section - checkbox and its (optional) helper caption grouped in
              one small-gap column, so the caption sits directly under the checkbox and no space
              is reserved for it when it isn't shown at all. */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
            <FormControlLabel
              sx={{ m: 0 }}
              control={
                <Checkbox
                  checked={makeDefault}
                  disabled={address?.defaultAddress}
                  onChange={(e) => setMakeDefault(e.target.checked)}
                />
              }
              label="Make this my default address"
            />
            {address?.defaultAddress && (
              <Typography variant="caption" color="text.secondary" sx={{ pl: 4.5 }}>
                This is already your default address.
              </Typography>
            )}
          </Box>
        </DialogContent>
        <DialogActions disableSpacing sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1.5, px: 3, py: 2 }}>
          <Button onClick={closeForm} disabled={submitting} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? 'Saving...' : 'Save Address'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
