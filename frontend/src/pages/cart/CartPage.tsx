import {
  Box, Paper, Typography, IconButton, Button, Divider, List, ListItem, Alert,
  CircularProgress, Skeleton, Chip,
} from '@mui/material'
import {
  Add, Remove, DeleteOutline, ShoppingCartOutlined, ArrowForward, Storefront,
} from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { cartService } from '../../services/cartService'
import { formatCurrency } from '../../utils/formatters'
import type { CartItemResponse } from '../../types/cart.types'

/** Customer-facing cart - the only place a multi-item purchase is assembled before checkout.
 *  Reuses order-service's real Cart API end to end (see cartService); there is no local/optimistic
 *  cart state here beyond what react-query caches, since price/availability/stock can change
 *  between page loads and must always be re-resolved from the server, never trusted from a
 *  previous render. */
export function CartPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { enqueueSnackbar } = useSnackbar()

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['cart'],
    queryFn: () => cartService.getCart(),
  })
  const cart = data?.data.data
  const items = cart?.items ?? []
  const hasUnavailable = items.some((i) => !i.available)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['cart'] })

  const updateMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: string; quantity: number }) =>
      cartService.updateItem(itemId, { quantity }),
    onSuccess: invalidate,
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? 'Could not update quantity.', { variant: 'error' }),
  })

  const removeMutation = useMutation({
    mutationFn: (itemId: string) => cartService.removeItem(itemId),
    onSuccess: () => { invalidate(); enqueueSnackbar('Removed from cart', { variant: 'success' }) },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? 'Could not remove this item.', { variant: 'error' }),
  })

  const changeQuantity = (item: CartItemResponse, delta: number) => {
    const next = item.quantity + delta
    if (next <= 0) { removeMutation.mutate(item.id); return }
    updateMutation.mutate({ itemId: item.id, quantity: next })
  }

  const availableSubtotal = cart?.subtotal ?? 0

  return (
    <Box>
      <PageHeader title="My Cart" subtitle={cart && items.length > 0 ? `${cart.itemCount} item${cart.itemCount === 1 ? '' : 's'}` : undefined} />

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Unable to load your cart
        </Alert>
      ) : isLoading ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} variant="rectangular" height={88} sx={{ borderRadius: 2 }} />)}
        </Box>
      ) : items.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <ShoppingCartOutlined sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>Your cart is empty</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Browse the shop and add products to get started.
          </Typography>
          <Button variant="contained" startIcon={<Storefront />} onClick={() => navigate('/products')}>
            Go to Shop
          </Button>
        </Paper>
      ) : (
        <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
          <Box sx={{ flex: 2, minWidth: 0 }}>
            {hasUnavailable && (
              <Alert severity="warning" sx={{ mb: 2 }}>
                Some items in your cart are no longer available and are excluded from your total.
              </Alert>
            )}
            <Paper variant="outlined" sx={{ borderRadius: 2 }}>
              <List disablePadding>
                {items.map((item, idx) => (
                  <ListItem
                    key={item.id}
                    divider={idx < items.length - 1}
                    sx={{ py: 2, opacity: item.available ? 1 : 0.6, flexWrap: 'wrap', gap: 1 }}
                  >
                    <Box
                      sx={{
                        width: 64, height: 64, borderRadius: 1.5, bgcolor: 'action.hover', flexShrink: 0,
                        display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', mr: 2,
                      }}
                    >
                      {item.imageUrl ? (
                        <Box component="img" src={item.imageUrl} alt={item.productName ?? ''} sx={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                      ) : (
                        <Storefront color="disabled" />
                      )}
                    </Box>

                    <Box sx={{ flex: 1, minWidth: 160 }}>
                      <Typography variant="body1" fontWeight={600}>{item.productName ?? 'Product'}</Typography>
                      {item.available ? (
                        <Typography variant="body2" color="text.secondary">
                          {formatCurrency(item.unitPrice)} {item.unit ? `/ ${item.unit}` : ''}
                        </Typography>
                      ) : (
                        <Chip size="small" color="error" label={item.unavailableReason ?? 'Unavailable'} sx={{ mt: 0.5 }} />
                      )}
                    </Box>

                    {item.available && (
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, border: '1px solid', borderColor: 'divider', borderRadius: 1.5 }}>
                        <IconButton size="small" onClick={() => changeQuantity(item, -1)} disabled={updateMutation.isPending || removeMutation.isPending}>
                          <Remove fontSize="small" />
                        </IconButton>
                        <Typography variant="body2" fontWeight={600} sx={{ minWidth: 24, textAlign: 'center' }}>
                          {item.quantity}
                        </Typography>
                        <IconButton size="small" onClick={() => changeQuantity(item, 1)} disabled={updateMutation.isPending || removeMutation.isPending}>
                          <Add fontSize="small" />
                        </IconButton>
                      </Box>
                    )}

                    <Typography variant="body1" fontWeight={700} sx={{ minWidth: 90, textAlign: 'right' }}>
                      {item.available ? formatCurrency(item.subtotal) : '—'}
                    </Typography>

                    <IconButton size="small" onClick={() => removeMutation.mutate(item.id)} disabled={removeMutation.isPending} aria-label={`Remove ${item.productName ?? 'item'}`}>
                      <DeleteOutline fontSize="small" />
                    </IconButton>
                  </ListItem>
                ))}
              </List>
            </Paper>
          </Box>

          <Box sx={{ flex: 1, minWidth: { md: 300 } }}>
            <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, position: { md: 'sticky' }, top: { md: 88 } }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Order Summary</Typography>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">Subtotal</Typography>
                <Typography variant="body2" fontWeight={600}>{formatCurrency(availableSubtotal)}</Typography>
              </Box>
              <Divider sx={{ my: 1.5 }} />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
                <Typography variant="subtitle1" fontWeight={700}>{formatCurrency(availableSubtotal)}</Typography>
              </Box>
              <Button
                variant="contained" fullWidth size="large" endIcon={<ArrowForward />}
                disabled={items.every((i) => !i.available)}
                onClick={() => navigate('/checkout')}
              >
                Proceed to Checkout
              </Button>
              {removeMutation.isPending || updateMutation.isPending ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1.5 }}><CircularProgress size={18} /></Box>
              ) : null}
            </Paper>
          </Box>
        </Box>
      )}
    </Box>
  )
}
