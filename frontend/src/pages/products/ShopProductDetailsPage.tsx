import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Divider, IconButton, Alert,
  Rating, LinearProgress, List, ListItem, ListItemText, CircularProgress,
} from '@mui/material'
import { ArrowBack, Storefront, ShoppingCartOutlined, AddShoppingCart, Add, Remove } from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { productService } from '../../services/productService'
import { reviewService } from '../../services/reviewService'
import { cartService } from '../../services/cartService'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { PRODUCT_UNIT_LABELS } from '../../types/product.types'
import { BuyNowDialog } from '../../components/order/BuyNowDialog'

/** Customer-facing product detail - read-only counterpart to ProductDetailsPage.tsx (the admin
 *  version, which has Edit/stock-quantity/product-ID/audit fields that don't belong in a
 *  customer-facing view). Shares the same GET /inventory/products/{id} and review APIs, just
 *  renders a different, non-admin layout - no separate backend endpoint. */
export function ShopProductDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { enqueueSnackbar } = useSnackbar()
  const [buyNowOpen, setBuyNowOpen] = useState(false)
  const [cartQuantity, setCartQuantity] = useState(1)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['products', id],
    queryFn: () => productService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const product = data?.data.data

  const { data: ratingRes } = useQuery({
    queryKey: ['reviews', 'rating-summary', id],
    queryFn: () => reviewService.ratingSummary(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const ratingSummary = ratingRes?.data.data

  const { data: reviewsRes, isLoading: reviewsLoading } = useQuery({
    queryKey: ['reviews', 'product', id],
    queryFn: () => reviewService.findByProduct(id!, { size: 10 }),
    enabled: Boolean(id),
    retry: false,
  })
  const reviews = reviewsRes?.data.data.content ?? []

  // Alongside Buy Now (direct single-item purchase), not a replacement for it - adds to the
  // caller's own server-side Cart (see cartService/CartPage). Quantity/availability/stock are all
  // re-validated server-side regardless of what's selected here.
  const addToCartMutation = useMutation({
    mutationFn: () => cartService.addItem({ productId: id!, quantity: cartQuantity }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] })
      enqueueSnackbar(`${product?.name ?? 'Product'} added to cart`, { variant: 'success' })
      setCartQuantity(1)
    },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? 'Could not add to cart.', { variant: 'error' }),
  })

  if (isLoading) {
    return (
      <Box>
        <Skeleton width={160} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={320} sx={{ borderRadius: 2 }} />
      </Box>
    )
  }

  if (isError || !product) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/products')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This product doesn't exist or has been removed." : "Couldn't load this product. Please try again."}
        </Alert>
      </Box>
    )
  }

  return (
    <Box>
      <IconButton onClick={() => navigate('/products')} sx={{ mb: 2 }}><ArrowBack /></IconButton>

      <Grid container spacing={3}>
        <Grid item xs={12} md={5}>
          <Box
            sx={{
              width: '100%', height: { xs: 260, md: 340 }, bgcolor: 'action.hover', borderRadius: 2,
              overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            {product.imageUrl ? (
              <Box
                component="img"
                src={product.imageUrl}
                alt={product.name}
                // contain, not MUI Avatar's default cover crop - see ShopProductCard.tsx/
                // ProductCard.tsx for the same fix on the Shop grid this page is one click from.
                sx={{ width: '100%', height: '100%', objectFit: 'contain', objectPosition: 'center' }}
              />
            ) : (
              <Storefront sx={{ fontSize: 64 }} color="disabled" />
            )}
          </Box>
        </Grid>

        <Grid item xs={12} md={7}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
            {product.categoryName && <Chip label={product.categoryName} size="small" variant="outlined" />}
            <Chip
              label={product.availability ? 'Available' : 'Currently Unavailable'}
              size="small"
              color={product.availability ? 'success' : 'default'}
            />
          </Box>
          <Typography variant="h4" fontWeight={700} gutterBottom>{product.name}</Typography>

          {ratingSummary && ratingSummary.totalReviews > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
              <Rating value={ratingSummary.averageRating} precision={0.1} readOnly size="small" />
              <Typography variant="body2" color="text.secondary">
                {ratingSummary.averageRating.toFixed(1)} ({ratingSummary.totalReviews} review{ratingSummary.totalReviews === 1 ? '' : 's'})
              </Typography>
            </Box>
          )}

          {product.description && (
            <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>{product.description}</Typography>
          )}

          <Typography variant="h4" fontWeight={700} sx={{ mb: 3 }}>
            {formatCurrency(product.price)}
            <Typography component="span" variant="body1" color="text.secondary"> / {PRODUCT_UNIT_LABELS[product.unit]}</Typography>
          </Typography>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, border: '1px solid', borderColor: 'divider', borderRadius: 1.5 }}>
              <IconButton size="small" onClick={() => setCartQuantity((q) => Math.max(1, q - 1))} disabled={!product.availability}>
                <Remove fontSize="small" />
              </IconButton>
              <Typography variant="body2" fontWeight={600} sx={{ minWidth: 28, textAlign: 'center' }}>{cartQuantity}</Typography>
              <IconButton size="small" onClick={() => setCartQuantity((q) => Math.min(50, q + 1))} disabled={!product.availability}>
                <Add fontSize="small" />
              </IconButton>
            </Box>
            <Button
              variant="outlined" size="large" startIcon={addToCartMutation.isPending ? <CircularProgress size={18} /> : <AddShoppingCart />}
              onClick={() => addToCartMutation.mutate()} disabled={!product.availability || addToCartMutation.isPending}
              fullWidth
            >
              Add to Cart
            </Button>
          </Box>

          <Button
            variant="contained" size="large" startIcon={<ShoppingCartOutlined />}
            onClick={() => setBuyNowOpen(true)} disabled={!product.availability}
            fullWidth
          >
            Buy Now
          </Button>
        </Grid>
      </Grid>

      <BuyNowDialog open={buyNowOpen} product={product} onClose={() => setBuyNowOpen(false)} />

      <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mt: 4 }}>
        <Typography variant="subtitle1" fontWeight={700} gutterBottom>Ratings & Reviews</Typography>
        <Divider sx={{ mb: 2 }} />

        {ratingSummary && ratingSummary.totalReviews > 0 ? (
          <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap', mb: 3 }}>
            <Box sx={{ textAlign: 'center', minWidth: 120 }}>
              <Typography variant="h3" fontWeight={700}>{ratingSummary.averageRating.toFixed(1)}</Typography>
              <Rating value={ratingSummary.averageRating} precision={0.1} readOnly />
              <Typography variant="caption" color="text.secondary" display="block">
                {ratingSummary.totalReviews} review{ratingSummary.totalReviews === 1 ? '' : 's'}
              </Typography>
            </Box>
            <Box sx={{ flex: 1, minWidth: 220, maxWidth: 360 }}>
              {([5, 4, 3, 2, 1] as const).map((star) => {
                const count = ratingSummary[`rating${star}Count` as const]
                const pct = ratingSummary.totalReviews > 0 ? (count / ratingSummary.totalReviews) * 100 : 0
                return (
                  <Box key={star} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                    <Typography variant="caption" sx={{ width: 12 }}>{star}</Typography>
                    <LinearProgress variant="determinate" value={pct} sx={{ flex: 1, height: 6, borderRadius: 1 }} />
                    <Typography variant="caption" color="text.secondary" sx={{ width: 24, textAlign: 'right' }}>{count}</Typography>
                  </Box>
                )
              })}
            </Box>
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>No reviews yet</Typography>
        )}

        {reviewsLoading ? (
          <Skeleton variant="rectangular" height={80} sx={{ borderRadius: 1 }} />
        ) : reviews.length > 0 && (
          <List disablePadding>
            {reviews.map((r) => (
              <ListItem key={r.id} disableGutters divider alignItems="flex-start">
                <ListItemText
                  primary={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Rating value={r.rating} size="small" readOnly />
                      <Typography variant="body2" fontWeight={600}>{r.customerDisplayName ?? 'Customer'}</Typography>
                    </Box>
                  }
                  secondary={
                    <>
                      {r.reviewText && (
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{r.reviewText}</Typography>
                      )}
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                        {formatDateTime(r.createdAt)}
                      </Typography>
                    </>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}
      </Paper>
    </Box>
  )
}
