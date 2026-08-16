import { Box, Card, CardContent, Typography, IconButton, Tooltip, CircularProgress } from '@mui/material'
import { Storefront, Add, AddShoppingCart } from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { formatCurrency } from '../../utils/formatters'
import { PRODUCT_UNIT_LABELS } from '../../types/product.types'
import type { Product } from '../../types/product.types'
import { BuyNowDialog } from '../order/BuyNowDialog'
import { cartService } from '../../services/cartService'

interface Props {
  product: Product
}

/** Customer-facing card - no admin menu, no Active/Inactive chip (the Shop only ever queries
 *  active+available products - see ShopPage), and no star rating (rating-summary has no bulk
 *  endpoint; showing it here would mean one request per card - see ProductDetailsPage, which is
 *  the only place ratings are shown, matching the N+1 decision already made for this feature).
 *  No attribute tags (e.g. "Organic"/"Pasture Raised") either - Product has no tags field, and
 *  showing them would mean fabricating claims about a product with nothing backing them. */
export function ShopProductCard({ product }: Props) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { enqueueSnackbar } = useSnackbar()
  const [buyNowOpen, setBuyNowOpen] = useState(false)
  const goToDetails = () => navigate(`/products/${product.id}`)

  // Alongside Buy Now (direct single-item purchase), not a replacement for it - adds one unit to
  // the caller's own server-side Cart (see cartService/CartPage). Quantity/availability/stock are
  // all re-validated server-side regardless of what's shown here.
  const addToCartMutation = useMutation({
    mutationFn: () => cartService.addItem({ productId: product.id, quantity: 1 }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] })
      enqueueSnackbar(`${product.name} added to cart`, { variant: 'success' })
    },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? 'Could not add to cart.', { variant: 'error' }),
  })

  return (
    <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column', borderRadius: 2, overflow: 'hidden' }}>
      <Box sx={{ cursor: 'pointer', position: 'relative', height: 160, bgcolor: 'action.hover', flexShrink: 0 }} onClick={goToDetails}>
        {product.imageUrl ? (
          <Box
            component="img"
            src={product.imageUrl}
            alt={product.name}
            // contain, not MUI Avatar's default cover crop - the full uploaded image must stay
            // visible (see ProductCard.tsx, the admin card, which had the identical bug and the
            // same fix). object-position centers it within this fixed-height box so every card
            // in the grid stays the same size regardless of the source image's own aspect ratio.
            sx={{ width: '100%', height: '100%', objectFit: 'contain', objectPosition: 'center' }}
          />
        ) : (
          <Box sx={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Storefront sx={{ fontSize: 40 }} color="disabled" />
          </Box>
        )}
      </Box>

      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        <Typography
          variant="subtitle1" fontWeight={700} sx={{ cursor: 'pointer' }}
          onClick={goToDetails}
        >
          {product.name}
        </Typography>
        {product.description && (
          <Typography
            variant="body2" color="text.secondary"
            sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
          >
            {product.description}
          </Typography>
        )}

        <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', mt: 'auto', pt: 1 }}>
          <Typography variant="h6" fontWeight={700}>
            {formatCurrency(product.price)}
            <Typography component="span" variant="caption" color="text.secondary"> / {PRODUCT_UNIT_LABELS[product.unit]}</Typography>
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.75 }}>
            <Tooltip title="Add to cart">
              <IconButton
                size="small"
                onClick={() => addToCartMutation.mutate()}
                disabled={addToCartMutation.isPending}
                aria-label={`Add ${product.name} to cart`}
                sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}
              >
                {addToCartMutation.isPending ? <CircularProgress size={16} /> : <AddShoppingCart fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Buy now">
              <IconButton
                size="small"
                onClick={() => setBuyNowOpen(true)}
                aria-label={`Buy ${product.name} now`}
                sx={{ bgcolor: 'text.primary', color: 'background.paper', borderRadius: 1, '&:hover': { bgcolor: 'text.secondary' } }}
              >
                <Add fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </CardContent>

      <BuyNowDialog open={buyNowOpen} product={product} onClose={() => setBuyNowOpen(false)} />
    </Card>
  )
}
