import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, MenuItem,
  Box, Typography, CircularProgress, Avatar, Switch, FormControlLabel, Alert,
} from '@mui/material'
import { PhotoCamera, Storefront, Close } from '@mui/icons-material'
import { useEffect, useRef, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useSnackbar } from 'notistack'
import { useQuery } from '@tanstack/react-query'
import { productService } from '../../services/productService'
import { productCategoryService } from '../../services/productCategoryService'
import { PRODUCT_UNITS, PRODUCT_UNIT_LABELS } from '../../types/product.types'
import type { Product, ProductUnit } from '../../types/product.types'

const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const schema = yup.object({
  name: yup.string().trim().required('Product name is required').max(100, 'Max 100 characters'),
  description: yup.string().max(2000, 'Description is too long'),
  // categoryId is intentionally optional here - the backend allows uncategorized products
  // (Product.category is nullable), so the form doesn't force a stricter rule than the API does.
  categoryId: yup.string(),
  price: yup.number()
    .typeError('Price is required')
    .required('Price is required')
    .moreThan(0, 'Price must be greater than 0'),
  unit: yup.string().oneOf(PRODUCT_UNITS, 'Unit is required').required('Unit is required'),
  stockQuantity: yup.number().typeError('Must be a number').min(0, 'Cannot be negative').default(0),
  minimumStockQuantity: yup.number().typeError('Must be a number').min(0, 'Cannot be negative').default(0),
  active: yup.boolean().default(true),
})

type FormValues = yup.InferType<typeof schema>

interface Props {
  open: boolean
  product: Product | null
  onClose: () => void
  onSaved: () => void
}

export function ProductFormDialog({ open, product, onClose, onSaved }: Props) {
  const { enqueueSnackbar } = useSnackbar()
  const isEdit = Boolean(product)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [imageError, setImageError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState('')

  const { data: categoriesRes, isLoading: categoriesLoading } = useQuery({
    queryKey: ['product-categories', 'active', 'form'],
    queryFn: () => productCategoryService.getAll({ activeOnly: true, size: 100 }),
    enabled: open,
  })
  const categories = categoriesRes?.data.data.content ?? []

  const {
    control, register, handleSubmit, reset, formState: { errors, isSubmitting: formSubmitting },
  } = useForm<FormValues>({
    resolver: yupResolver(schema),
    defaultValues: { name: '', description: '', categoryId: '', price: undefined, unit: undefined, stockQuantity: 0, minimumStockQuantity: 0, active: true },
  })

  useEffect(() => {
    if (!open) return
    setServerError('')
    setImageFile(null)
    setImageError('')
    setImagePreview(product?.imageUrl ?? null)
    reset({
      name: product?.name ?? '',
      description: product?.description ?? '',
      categoryId: product?.categoryId ?? '',
      price: product?.price,
      unit: product?.unit,
      stockQuantity: product?.stockQuantity ?? 0,
      minimumStockQuantity: product?.minimumStockQuantity ?? 0,
      active: product?.active ?? true,
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, product])

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setImageError('')
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      setImageError('Only JPEG, PNG, or WEBP images are allowed.')
      return
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      setImageError('Image must be smaller than 5 MB.')
      return
    }
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  const onSubmit = async (values: FormValues) => {
    setServerError('')
    setSubmitting(true)
    try {
      const payload = {
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
        categoryId: values.categoryId || undefined,
        price: values.price,
        unit: values.unit as ProductUnit,
        stockQuantity: values.stockQuantity,
        minimumStockQuantity: values.minimumStockQuantity,
      }

      let productId = product?.id
      if (isEdit && product) {
        await productService.update(product.id, { ...payload, active: values.active })
      } else {
        const created = await productService.create(payload)
        productId = created.data.data.id
      }

      if (imageFile && productId) {
        await productService.uploadImage(productId, imageFile)
      }

      enqueueSnackbar(isEdit ? 'Product updated successfully' : 'Product created successfully', { variant: 'success' })
      onSaved()
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const busy = submitting || formSubmitting

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>{isEdit ? 'Edit Product' : 'Add Product'}</DialogTitle>
      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar
              src={imagePreview ?? undefined}
              variant="rounded"
              sx={{ width: 72, height: 72, bgcolor: 'action.hover' }}
            >
              <Storefront color="disabled" />
            </Avatar>
            <Box>
              <Button
                size="small"
                variant="outlined"
                startIcon={<PhotoCamera />}
                onClick={() => fileInputRef.current?.click()}
              >
                {imagePreview ? 'Replace Image' : 'Upload Image'}
              </Button>
              <input ref={fileInputRef} type="file" accept={ACCEPTED_IMAGE_TYPES.join(',')} hidden onChange={handleFileSelect} />
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                JPEG, PNG, or WEBP. Max 5 MB.
              </Typography>
              {imageError && <Typography variant="caption" color="error" display="block">{imageError}</Typography>}
            </Box>
          </Box>

          <TextField
            label="Product Name" required fullWidth size="small"
            error={!!errors.name} helperText={errors.name?.message}
            {...register('name')}
          />

          <TextField
            label="Description" fullWidth multiline minRows={2} size="small"
            error={!!errors.description} helperText={errors.description?.message}
            {...register('description')}
          />

          <Box sx={{ display: 'flex', gap: 2 }}>
            <Controller
              name="categoryId"
              control={control}
              render={({ field }) => {
                // The update API has no way to clear an already-set category (omitted/unchanged
                // is the only meaning of an empty categoryId there) - once a product has one,
                // "Uncategorized" would silently do nothing, so it's only offered when there's
                // genuinely nothing to clear.
                const canClearCategory = !isEdit || !product?.categoryId
                return (
                  <TextField
                    {...field}
                    select label="Category" fullWidth size="small"
                    disabled={categoriesLoading}
                    helperText={categoriesLoading ? 'Loading categories…' : canClearCategory ? 'Optional' : 'Can be changed, not cleared, once set'}
                  >
                    {canClearCategory && <MenuItem value="">Uncategorized</MenuItem>}
                    {categories.map((c) => (
                      <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>
                    ))}
                  </TextField>
                )
              }}
            />
            <Controller
              name="unit"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  value={field.value ?? ''}
                  select label="Unit" required fullWidth size="small"
                  error={!!errors.unit} helperText={errors.unit?.message}
                >
                  {PRODUCT_UNITS.map((u) => (
                    <MenuItem key={u} value={u}>{PRODUCT_UNIT_LABELS[u]}</MenuItem>
                  ))}
                </TextField>
              )}
            />
          </Box>

          <TextField
            label="Price (₹)" type="number" required fullWidth size="small"
            inputProps={{ step: '0.01', min: 0 }}
            error={!!errors.price} helperText={errors.price?.message}
            {...register('price')}
          />

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Stock Quantity" type="number" fullWidth size="small"
              inputProps={{ min: 0 }}
              error={!!errors.stockQuantity} helperText={errors.stockQuantity?.message}
              {...register('stockQuantity')}
            />
            <TextField
              label="Minimum Stock Quantity" type="number" fullWidth size="small"
              inputProps={{ min: 0 }}
              error={!!errors.minimumStockQuantity} helperText={errors.minimumStockQuantity?.message}
              {...register('minimumStockQuantity')}
            />
          </Box>

          {isEdit && (
            <Controller
              name="active"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />}
                  label={field.value ? 'Active' : 'Inactive'}
                />
              )}
            />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={busy} color="inherit" startIcon={<Close />}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy}>
            {busy ? <CircularProgress size={20} color="inherit" /> : isEdit ? 'Save Changes' : 'Create Product'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
