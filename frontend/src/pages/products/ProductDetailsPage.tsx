import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Avatar, Divider, IconButton, Alert,
} from '@mui/material'
import { ArrowBack, Edit, Storefront, AddCircleOutline, EditCalendar } from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { productService } from '../../services/productService'
import { ProductFormDialog } from '../../components/product/ProductFormDialog'
import { useAuth } from '../../hooks/useAuth'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { PRODUCT_UNIT_LABELS, STOCK_STATUS_LABELS } from '../../types/product.types'
import type { ProductStockStatus } from '../../types/product.types'

const STOCK_STATUS_COLOR: Record<ProductStockStatus, 'success' | 'warning' | 'error'> = {
  IN_STOCK: 'success', LOW_STOCK: 'warning', OUT_OF_STOCK: 'error',
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, height: '100%' }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>{title}</Typography>
      <Divider sx={{ mb: 2 }} />
      {children}
    </Paper>
  )
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
      <Typography variant="body2" fontWeight={500}>{value}</Typography>
    </Box>
  )
}

export function ProductDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const canWrite = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false
  const [editOpen, setEditOpen] = useState(false)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['products', id],
    queryFn: () => productService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const product = data?.data.data

  const handleSaved = () => {
    setEditOpen(false)
    queryClient.invalidateQueries({ queryKey: ['products'] })
  }

  if (isLoading) {
    return (
      <Box>
        <Skeleton width={160} height={40} sx={{ mb: 2 }} />
        <Grid container spacing={2}>
          {Array.from({ length: 4 }).map((_, i) => (
            <Grid item xs={12} md={6} key={i}><Skeleton variant="rectangular" height={180} sx={{ borderRadius: 2 }} /></Grid>
          ))}
        </Grid>
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
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/products')}><ArrowBack /></IconButton>
          <Avatar src={product.imageUrl ?? undefined} variant="rounded" sx={{ width: 56, height: 56, bgcolor: 'action.hover' }}>
            <Storefront color="disabled" />
          </Avatar>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>{product.name}</Typography>
              <Chip label={product.active ? 'Active' : 'Inactive'} size="small" color={product.active ? 'success' : 'default'} />
            </Box>
            <Typography variant="body2" color="text.secondary">{product.categoryName ?? 'Uncategorized'}</Typography>
          </Box>
        </Box>
        {canWrite && (
          <Button variant="contained" startIcon={<Edit />} onClick={() => setEditOpen(true)}>Edit</Button>
        )}
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Overview">
            <Field label="Description" value={product.description || 'No description provided.'} />
            <Field label="Category" value={product.categoryName ?? 'Uncategorized'} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Pricing">
            <Field label="Price" value={`${formatCurrency(product.price)} / ${product.unit}`} />
            <Field label="Unit" value={`${PRODUCT_UNIT_LABELS[product.unit]} (${product.unit})`} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Stock">
            <Field label="Current Stock" value={`${product.stockQuantity} ${product.unit}`} />
            <Field label="Minimum Stock" value={`${product.minimumStockQuantity} ${product.unit}`} />
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">Stock Status</Typography>
              <Chip
                label={STOCK_STATUS_LABELS[product.stockStatus]}
                size="small"
                color={STOCK_STATUS_COLOR[product.stockStatus]}
                sx={{ fontWeight: 600, mt: 0.5 }}
              />
            </Box>
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Availability">
            <Chip
              label={product.availability ? 'Available' : 'Unavailable'}
              color={product.availability ? 'success' : 'default'}
              sx={{ fontWeight: 600, mb: 1.5 }}
            />
            <Typography variant="body2" color="text.secondary">
              Determined by the backend from the product's active status and current stock -
              available only while the product is active and in stock.
            </Typography>
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Product Information">
            <Field label="Product ID" value={<Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{product.id}</Typography>} />
            <Field label="Created" value={formatDateTime(product.createdAt)} />
            <Field label="Last Updated" value={formatDateTime(product.updatedAt)} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Activity">
            <Box sx={{ display: 'flex', gap: 1.5, mb: 1.5 }}>
              <AddCircleOutline fontSize="small" color="action" sx={{ mt: 0.25 }} />
              <Box>
                <Typography variant="body2">Product created</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(product.createdAt)}</Typography>
              </Box>
            </Box>
            {product.updatedAt !== product.createdAt && (
              <Box sx={{ display: 'flex', gap: 1.5 }}>
                <EditCalendar fontSize="small" color="action" sx={{ mt: 0.25 }} />
                <Box>
                  <Typography variant="body2">Last updated</Typography>
                  <Typography variant="caption" color="text.secondary">{formatDateTime(product.updatedAt)}</Typography>
                </Box>
              </Box>
            )}
          </Section>
        </Grid>
      </Grid>

      <ProductFormDialog open={editOpen} product={product} onClose={() => setEditOpen(false)} onSaved={handleSaved} />
    </Box>
  )
}
