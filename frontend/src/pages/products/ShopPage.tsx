import {
  Box, TextField, MenuItem, InputAdornment, IconButton, Grid, Paper, Typography, Button,
  Pagination, Alert, CircularProgress, Chip,
} from '@mui/material'
import { Search, Close, Storefront, CheckCircle, LocationOn, HelpOutline } from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { PageHeader } from '../../components/common/PageHeader'
import { ShopProductCard } from '../../components/product/ShopProductCard'
import { DeliveryAddressDialog } from '../../components/product/DeliveryAddressDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { productService } from '../../services/productService'
import { productCategoryService } from '../../services/productCategoryService'
import { customerService } from '../../services/customerService'

const PAGE_SIZE = 12
const DEBOUNCE_MS = 400

const SORT_OPTIONS = [
  { value: 'name,asc', label: 'Name (A-Z)' },
  { value: 'name,desc', label: 'Name (Z-A)' },
  { value: 'price,asc', label: 'Price (Low to High)' },
  { value: 'price,desc', label: 'Price (High to Low)' },
]

/** Customer-facing product catalog - reuses the exact same backend GET /inventory/products/search
 *  endpoint the admin Product Management screen (ProductsPage.tsx) uses; nothing here is a
 *  separate/duplicate API. The only thing specific to this page is the customer-visibility rule
 *  (active=true AND available=true, both hardcoded and not exposed as togglable filters, unlike
 *  the admin screen) - see DPDP-adjacent audit notes in the PR: the backend has no distinct
 *  "customer visible" flag, so this uses the existing active/availability fields the backend
 *  already computes, rather than inventing a new rule. */
export function ShopPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [sort, setSort] = useState('name,asc')
  const [addressDialogOpen, setAddressDialogOpen] = useState(false)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const hasActiveFilters = Boolean(debouncedSearch || categoryFilter)

  // firstName isn't on the auth user (only customer-service has it) - same pattern
  // OrderDetailsPage already uses for a different customer id: customerService.getById(), just
  // pointed at the caller's own id (== auth user id, see Customer.id convention).
  const { data: customerRes } = useQuery({
    queryKey: ['customers', user?.id, 'me'],
    queryFn: () => customerService.getById(user!.id),
    enabled: Boolean(user?.id),
    retry: false,
  })
  const customer = customerRes?.data.data

  const { data: addressesRes } = useQuery({
    queryKey: ['customers', user?.id, 'addresses'],
    queryFn: () => customerService.getAddresses(user!.id),
    enabled: Boolean(user?.id),
  })
  const defaultAddress = addressesRes?.data.data.find((a) => a.defaultAddress)

  // Backend-authoritative 10 KM delivery-radius check against the customer's default address -
  // see customer-service's DeliveryAvailabilityServiceImpl for the actual Haversine calculation.
  // Google Maps (see TrackingMap.tsx elsewhere) plays no part in this; this is the real check.
  const { data: availabilityRes, isLoading: availabilityLoading } = useQuery({
    queryKey: ['customers', user?.id, 'delivery-availability'],
    queryFn: () => customerService.getDeliveryAvailability(),
    enabled: Boolean(user?.id),
    retry: false,
  })
  const availability = availabilityRes?.data.data

  const refetchAvailability = () => {
    queryClient.invalidateQueries({ queryKey: ['customers', user?.id, 'delivery-availability'] })
    queryClient.invalidateQueries({ queryKey: ['customers', user?.id, 'addresses'] })
  }

  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    categoryId: categoryFilter || undefined,
    active: true,
    available: true,
    page, size: PAGE_SIZE, sort,
  }), [debouncedSearch, categoryFilter, page, sort])

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['products', 'shop', searchParams],
    queryFn: () => productService.search(searchParams),
  })

  const { data: categoriesRes } = useQuery({
    queryKey: ['product-categories', 'active', 'shop-filter'],
    queryFn: () => productCategoryService.getAll({ activeOnly: true, size: 100 }),
  })
  const categories = categoriesRes?.data.data.content ?? []

  const products = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const resetFilters = () => { setSearch(''); setCategoryFilter(''); setPage(0) }

  return (
    <Box>
      <PageHeader
        title="Shop"
        subtitle={customer ? `Welcome back, ${customer.firstName}` : undefined}
      />

      <Paper
        variant="outlined"
        sx={{ p: 2, mb: 3, borderRadius: 2, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1.5 }}
      >
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
          <LocationOn color="action" sx={{ mt: 0.25 }} />
          <Box>
            <Typography variant="caption" color="text.secondary" display="block">Delivering to</Typography>
            {defaultAddress ? (
              <Typography variant="body2" fontWeight={600}>
                {defaultAddress.addressLine1}, {defaultAddress.city}
              </Typography>
            ) : (
              <Typography variant="body2" color="text.secondary">No delivery address set</Typography>
            )}
            {!availabilityLoading && (
              availability?.deliveryAvailable === true ? (
                <Box sx={{ mt: 0.5, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                  <Chip size="small" color="success" icon={<CheckCircle sx={{ fontSize: 14 }} />} label="Delivery available" />
                  {availability.routeName && (
                    <Typography variant="caption" color="text.secondary">
                      Estimated delivery area: <strong>{availability.routeName}</strong>
                    </Typography>
                  )}
                </Box>
              ) : availability?.deliveryAvailable === false ? (
                <Box sx={{ mt: 0.5 }}>
                  <Chip size="small" color="error" label="Delivery unavailable" />
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                    You are {availability.distanceKm} km from Farm2Home. Our current delivery radius is {availability.deliveryRadiusKm} km.
                  </Typography>
                </Box>
              ) : (
                <Chip
                  size="small" variant="outlined" icon={<HelpOutline sx={{ fontSize: 14 }} />}
                  label={availability?.message ?? 'Delivery availability unknown'} sx={{ mt: 0.5 }}
                />
              )
            )}
          </Box>
        </Box>
        <Button size="small" onClick={() => setAddressDialogOpen(true)}>Change Address</Button>
      </Paper>

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search products…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          sx={{ minWidth: 260, flex: 1 }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
            endAdornment: search && (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearch('')}><Close fontSize="small" /></IconButton>
              </InputAdornment>
            ),
          }}
        />
        <TextField select size="small" label="Category" value={categoryFilter}
          onChange={(e) => { setCategoryFilter(e.target.value); setPage(0) }} sx={{ minWidth: 160 }}>
          <MenuItem value="">All Categories</MenuItem>
          {categories.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Sort By" value={sort}
          onChange={(e) => setSort(e.target.value)} sx={{ minWidth: 170 }}>
          {SORT_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
        </TextField>
      </Box>

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Unable to load products
        </Alert>
      ) : isLoading ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 8, gap: 2 }}>
          <CircularProgress />
          <Typography variant="body2" color="text.secondary">Loading products…</Typography>
        </Box>
      ) : products.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <Storefront sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No products match your search.' : 'No products available'}
          </Typography>
          {hasActiveFilters && <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>}
        </Paper>
      ) : (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'right', mb: 1 }}>
            Showing {products.length} of {totalElements.toLocaleString()} product{totalElements === 1 ? '' : 's'}
          </Typography>
          <Grid container spacing={2}>
            {products.map((p) => (
              <Grid item xs={12} sm={6} md={4} lg={3} key={p.id}>
                <ShopProductCard product={p} />
              </Grid>
            ))}
          </Grid>

          {totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
              <Pagination
                count={totalPages}
                page={page + 1}
                onChange={(_e, p) => setPage(p - 1)}
                color="primary"
                size="small"
              />
            </Box>
          )}
        </>
      )}

      {user?.id && (
        <DeliveryAddressDialog
          open={addressDialogOpen}
          customerId={user.id}
          onClose={() => setAddressDialogOpen(false)}
          onChanged={refetchAvailability}
        />
      )}
    </Box>
  )
}
