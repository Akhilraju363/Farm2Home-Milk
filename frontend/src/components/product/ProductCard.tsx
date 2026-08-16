import { Box, Card, CardContent, Typography, Chip, IconButton, Menu, MenuItem, ListItemIcon, ListItemText } from '@mui/material'
import { MoreVert, Storefront, Visibility, Edit, ToggleOff, ToggleOn } from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatters'
import { STOCK_STATUS_LABELS } from '../../types/product.types'
import type { Product, ProductStockStatus } from '../../types/product.types'

const STOCK_STATUS_COLOR: Record<ProductStockStatus, 'success' | 'warning' | 'error'> = {
  IN_STOCK: 'success',
  LOW_STOCK: 'warning',
  OUT_OF_STOCK: 'error',
}

interface Props {
  product: Product
  canWrite: boolean
  onEdit: (product: Product) => void
  onToggleActive: (product: Product) => void
}

export function ProductCard({ product, canWrite, onEdit, onToggleActive }: Props) {
  const navigate = useNavigate()
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)

  const closeMenu = () => setAnchorEl(null)

  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ position: 'relative', height: 140, bgcolor: 'action.hover', flexShrink: 0 }}>
        {product.imageUrl ? (
          <Box
            component="img"
            src={product.imageUrl}
            alt={product.name}
            // contain, not cover - a cover crop was cutting off the top/bottom/sides of whatever
            // an admin actually uploaded (see the milk bottle image that prompted this fix).
            // object-position centers it within the fixed-height box above so every card stays
            // the same size regardless of the source image's own aspect ratio.
            sx={{ width: '100%', height: '100%', objectFit: 'contain', objectPosition: 'center' }}
          />
        ) : (
          <Box sx={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Storefront sx={{ fontSize: 40, color: 'text.disabled' }} />
          </Box>
        )}
        <Chip
          label={product.active ? 'Active' : 'Inactive'}
          size="small"
          color={product.active ? 'success' : 'default'}
          sx={{ position: 'absolute', top: 8, left: 8, fontWeight: 600 }}
        />
        <IconButton
          size="small"
          onClick={(e) => setAnchorEl(e.currentTarget)}
          sx={{ position: 'absolute', top: 4, right: 4, bgcolor: 'background.paper', '&:hover': { bgcolor: 'background.paper' } }}
        >
          <MoreVert fontSize="small" />
        </IconButton>
        <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={closeMenu}>
          <MenuItem onClick={() => { closeMenu(); navigate(`/products/${product.id}`) }}>
            <ListItemIcon><Visibility fontSize="small" /></ListItemIcon>
            <ListItemText>View</ListItemText>
          </MenuItem>
          {canWrite && (
            <MenuItem onClick={() => { closeMenu(); onEdit(product) }}>
              <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
              <ListItemText>Edit</ListItemText>
            </MenuItem>
          )}
          {canWrite && (
            <MenuItem onClick={() => { closeMenu(); onToggleActive(product) }}>
              <ListItemIcon>{product.active ? <ToggleOff fontSize="small" /> : <ToggleOn fontSize="small" />}</ListItemIcon>
              <ListItemText>{product.active ? 'Deactivate' : 'Activate'}</ListItemText>
            </MenuItem>
          )}
        </Menu>
      </Box>

      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
        <Typography
          variant="subtitle1"
          fontWeight={700}
          sx={{ cursor: 'pointer' }}
          onClick={() => navigate(`/products/${product.id}`)}
        >
          {product.name}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {product.categoryName ?? 'Uncategorized'}
        </Typography>
        <Typography variant="h6" fontWeight={700} color="primary.main">
          {formatCurrency(product.price)} <Typography component="span" variant="body2" color="text.secondary">/ {product.unit}</Typography>
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 0.5 }}>
          <Typography variant="body2" color="text.secondary">
            {product.stockQuantity} {product.unit} in stock
          </Typography>
          <Chip
            label={STOCK_STATUS_LABELS[product.stockStatus]}
            size="small"
            color={STOCK_STATUS_COLOR[product.stockStatus]}
            sx={{ fontWeight: 600, fontSize: 11 }}
          />
        </Box>

        <Box sx={{ mt: 'auto', pt: 1 }}>
          <Chip
            label={product.availability ? 'Available' : 'Unavailable'}
            size="small"
            variant="outlined"
            color={product.availability ? 'success' : 'default'}
          />
        </Box>
      </CardContent>
    </Card>
  )
}
