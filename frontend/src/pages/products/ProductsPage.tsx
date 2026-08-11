import {
  Box, TextField, MenuItem, InputAdornment, IconButton, Tooltip, Grid, Skeleton, Paper,
  Typography, Button, ToggleButtonGroup, ToggleButton, Chip, Menu, ListItemIcon, ListItemText,
  Pagination, Alert, CircularProgress,
} from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import {
  Search, Refresh, Add, ViewModule, ViewList, Close, Category as CategoryIcon,
  Download, Storefront, MoreVert, Visibility, Edit, ToggleOff, ToggleOn,
} from '@mui/icons-material'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { ProductCard } from '../../components/product/ProductCard'
import { ProductFormDialog } from '../../components/product/ProductFormDialog'
import { useAuth } from '../../hooks/useAuth'
import { useDebounced } from '../../hooks/useDebounced'
import { productService } from '../../services/productService'
import { productCategoryService } from '../../services/productCategoryService'
import { downloadBlob } from '../../utils/download'
import { formatCurrency } from '../../utils/formatters'
import { STOCK_STATUS_LABELS } from '../../types/product.types'
import type { Product, ProductStockStatus } from '../../types/product.types'

const PAGE_SIZE = 12
const DEBOUNCE_MS = 400

const STOCK_STATUS_COLOR: Record<ProductStockStatus, 'success' | 'warning' | 'error'> = {
  IN_STOCK: 'success', LOW_STOCK: 'warning', OUT_OF_STOCK: 'error',
}

const SORT_OPTIONS = [
  { value: 'name,asc', label: 'Name (A-Z)' },
  { value: 'name,desc', label: 'Name (Z-A)' },
  { value: 'price,asc', label: 'Price (Low to High)' },
  { value: 'price,desc', label: 'Price (High to Low)' },
  { value: 'stockQuantity,asc', label: 'Stock (Low to High)' },
  { value: 'stockQuantity,desc', label: 'Stock (High to Low)' },
  { value: 'createdAt,desc', label: 'Newest First' },
  { value: 'createdAt,asc', label: 'Oldest First' },
]

function ProductCardSkeleton() {
  return (
    <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <Skeleton variant="rectangular" height={140} />
      <Box sx={{ p: 2 }}>
        <Skeleton width="70%" height={28} />
        <Skeleton width="40%" />
        <Skeleton width="50%" height={32} sx={{ mt: 1 }} />
        <Skeleton width="60%" />
      </Box>
    </Paper>
  )
}

export function ProductsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const canWrite = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [view, setView] = useState<'card' | 'table'>('card')
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [stockStatusFilter, setStockStatusFilter] = useState('')
  const [availableFilter, setAvailableFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [sort, setSort] = useState('name,asc')
  const [exporting, setExporting] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [toggleTarget, setToggleTarget] = useState<Product | null>(null)
  const [rowMenu, setRowMenu] = useState<{ anchor: HTMLElement; product: Product } | null>(null)

  const debouncedSearch = useDebounced(search, DEBOUNCE_MS)
  const hasActiveFilters = Boolean(debouncedSearch || categoryFilter || stockStatusFilter || availableFilter || activeFilter)

  const searchParams = useMemo(() => ({
    keyword: debouncedSearch.trim() || undefined,
    categoryId: categoryFilter || undefined,
    stockStatus: (stockStatusFilter || undefined) as ProductStockStatus | undefined,
    available: availableFilter === '' ? undefined : availableFilter === 'true',
    active: activeFilter === '' ? undefined : activeFilter === 'true',
    page, size: PAGE_SIZE, sort,
  }), [debouncedSearch, categoryFilter, stockStatusFilter, availableFilter, activeFilter, page, sort])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['products', 'search', searchParams],
    queryFn: () => productService.search(searchParams),
  })

  const { data: categoriesRes } = useQuery({
    queryKey: ['product-categories', 'active', 'filter'],
    queryFn: () => productCategoryService.getAll({ activeOnly: true, size: 100 }),
  })
  const categories = categoriesRes?.data.data.content ?? []

  const products = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0
  const totalPages = data?.data.data.totalPages ?? 0

  const toggleActiveMutation = useMutation({
    mutationFn: (p: Product) => productService.update(p.id, { active: !p.active }),
    onSuccess: (_res, p) => {
      enqueueSnackbar(p.active ? 'Product deactivated' : 'Product activated', { variant: 'success' })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      setToggleTarget(null)
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? 'Could not update the product. Please try again.', { variant: 'error' })
      setToggleTarget(null)
    },
  })

  const resetFilters = () => {
    setSearch(''); setCategoryFilter(''); setStockStatusFilter(''); setAvailableFilter(''); setActiveFilter('')
    setPage(0)
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      const { blob, filename } = await productService.export({ ...searchParams, format: 'CSV' })
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar("Couldn't export products. Please try again.", { variant: 'error' })
    } finally {
      setExporting(false)
    }
  }

  const openAdd = () => { setEditingProduct(null); setFormOpen(true) }
  const openEdit = (p: Product) => { setEditingProduct(p); setFormOpen(true) }
  const closeForm = () => setFormOpen(false)
  const handleSaved = () => {
    setFormOpen(false)
    queryClient.invalidateQueries({ queryKey: ['products'] })
  }

  const columns: GridColDef<Product>[] = [
    {
      field: 'name', headerName: 'Product', flex: 1.4, minWidth: 180,
      renderCell: ({ row }) => (
        <Box sx={{ cursor: 'pointer', py: 0.5 }} onClick={() => navigate(`/products/${row.id}`)}>
          <Typography variant="body2" fontWeight={600}>{row.name}</Typography>
          <Typography variant="caption" color="text.secondary">{row.categoryName ?? 'Uncategorized'}</Typography>
        </Box>
      ),
    },
    {
      field: 'price', headerName: 'Price', width: 130,
      renderCell: ({ row }) => `${formatCurrency(row.price)} / ${row.unit}`,
    },
    { field: 'stockQuantity', headerName: 'Stock', width: 100, type: 'number' },
    {
      field: 'stockStatus', headerName: 'Stock Status', width: 130,
      renderCell: ({ value }) => (
        <Chip label={STOCK_STATUS_LABELS[value as ProductStockStatus]} size="small" color={STOCK_STATUS_COLOR[value as ProductStockStatus]} sx={{ fontWeight: 600 }} />
      ),
    },
    {
      field: 'availability', headerName: 'Availability', width: 130,
      renderCell: ({ value }) => (
        <Chip label={value ? 'Available' : 'Unavailable'} size="small" variant="outlined" color={value ? 'success' : 'default'} />
      ),
    },
    {
      field: 'active', headerName: 'Status', width: 110,
      renderCell: ({ value }) => <Chip label={value ? 'Active' : 'Inactive'} size="small" color={value ? 'success' : 'default'} />,
    },
    {
      field: 'actions', headerName: '', width: 60, sortable: false, filterable: false,
      renderCell: ({ row }) => (
        <IconButton size="small" onClick={(e) => setRowMenu({ anchor: e.currentTarget, product: row })}>
          <MoreVert fontSize="small" />
        </IconButton>
      ),
    },
  ]

  return (
    <Box>
      <PageHeader
        title="Product Management"
        subtitle="Manage your customer-facing Farm2Home products, pricing, availability and stock."
        action={canWrite ? { label: 'Add Product', icon: <Add />, onClick: openAdd } : undefined}
      />

      <Box sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap' }}>
        <Button variant="outlined" startIcon={<CategoryIcon />} onClick={() => navigate('/products/categories')}>
          Categories
        </Button>
        <Button
          variant="outlined"
          startIcon={exporting ? <CircularProgress size={16} /> : <Download />}
          onClick={handleExport}
          disabled={exporting}
        >
          Export
        </Button>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small" sx={{ ml: 'auto' }}><Refresh /></IconButton>
        </Tooltip>
        <ToggleButtonGroup value={view} exclusive size="small" onChange={(_e, v) => v && setView(v)}>
          <ToggleButton value="card"><ViewModule fontSize="small" /></ToggleButton>
          <ToggleButton value="table"><ViewList fontSize="small" /></ToggleButton>
        </ToggleButtonGroup>
      </Box>

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search name, description, category…"
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
        <TextField select size="small" label="Stock Status" value={stockStatusFilter}
          onChange={(e) => { setStockStatusFilter(e.target.value); setPage(0) }} sx={{ minWidth: 150 }}>
          <MenuItem value="">All</MenuItem>
          <MenuItem value="IN_STOCK">In Stock</MenuItem>
          <MenuItem value="LOW_STOCK">Low Stock</MenuItem>
          <MenuItem value="OUT_OF_STOCK">Out of Stock</MenuItem>
        </TextField>
        <TextField select size="small" label="Availability" value={availableFilter}
          onChange={(e) => { setAvailableFilter(e.target.value); setPage(0) }} sx={{ minWidth: 140 }}>
          <MenuItem value="">All</MenuItem>
          <MenuItem value="true">Available</MenuItem>
          <MenuItem value="false">Unavailable</MenuItem>
        </TextField>
        <TextField select size="small" label="Status" value={activeFilter}
          onChange={(e) => { setActiveFilter(e.target.value); setPage(0) }} sx={{ minWidth: 130 }}>
          <MenuItem value="">All</MenuItem>
          <MenuItem value="true">Active</MenuItem>
          <MenuItem value="false">Inactive</MenuItem>
        </TextField>
        <TextField select size="small" label="Sort By" value={sort}
          onChange={(e) => setSort(e.target.value)} sx={{ minWidth: 170 }}>
          {SORT_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
        </TextField>
      </Box>

      {isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>Retry</Button>}>
          Couldn't load products. Please check your connection and try again.
        </Alert>
      ) : isLoading ? (
        view === 'card' ? (
          <Grid container spacing={2}>
            {Array.from({ length: 8 }).map((_, i) => (
              <Grid item xs={12} sm={6} md={4} lg={3} key={i}><ProductCardSkeleton /></Grid>
            ))}
          </Grid>
        ) : (
          <DataGrid rows={[]} columns={columns} loading autoHeight sx={{ bgcolor: 'background.paper', borderRadius: 2 }} />
        )
      ) : products.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <Storefront sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>
            {hasActiveFilters ? 'No products match your search.' : 'No products have been added yet.'}
          </Typography>
          {hasActiveFilters ? (
            <Button onClick={resetFilters} sx={{ mt: 1 }}>Clear Filters</Button>
          ) : canWrite ? (
            <Button variant="contained" startIcon={<Add />} onClick={openAdd} sx={{ mt: 1 }}>Add Product</Button>
          ) : null}
        </Paper>
      ) : view === 'card' ? (
        <>
          <Grid container spacing={2}>
            {products.map((p) => (
              <Grid item xs={12} sm={6} md={4} lg={3} key={p.id}>
                <ProductCard
                  product={p}
                  canWrite={canWrite}
                  onEdit={openEdit}
                  onToggleActive={setToggleTarget}
                />
              </Grid>
            ))}
          </Grid>
        </>
      ) : (
        <DataGrid
          rows={products}
          columns={columns}
          loading={isFetching}
          autoHeight
          hideFooter
          disableRowSelectionOnClick
          sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
        />
      )}

      {!isLoading && !isError && totalElements > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {totalElements.toLocaleString()} product{totalElements === 1 ? '' : 's'} • Page {page + 1} of {Math.max(totalPages, 1)}
          </Typography>
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={(_e, p) => setPage(p - 1)}
            color="primary"
            size="small"
          />
        </Box>
      )}

      <Menu anchorEl={rowMenu?.anchor} open={Boolean(rowMenu)} onClose={() => setRowMenu(null)}>
        <MenuItem onClick={() => { navigate(`/products/${rowMenu?.product.id}`); setRowMenu(null) }}>
          <ListItemIcon><Visibility fontSize="small" /></ListItemIcon>
          <ListItemText>View</ListItemText>
        </MenuItem>
        {canWrite && rowMenu && (
          <MenuItem onClick={() => { openEdit(rowMenu.product); setRowMenu(null) }}>
            <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
            <ListItemText>Edit</ListItemText>
          </MenuItem>
        )}
        {canWrite && rowMenu && (
          <MenuItem onClick={() => { setToggleTarget(rowMenu.product); setRowMenu(null) }}>
            <ListItemIcon>{rowMenu.product.active ? <ToggleOff fontSize="small" /> : <ToggleOn fontSize="small" />}</ListItemIcon>
            <ListItemText>{rowMenu.product.active ? 'Deactivate' : 'Activate'}</ListItemText>
          </MenuItem>
        )}
      </Menu>

      <ProductFormDialog open={formOpen} product={editingProduct} onClose={closeForm} onSaved={handleSaved} />

      <ConfirmDialog
        open={Boolean(toggleTarget)}
        title={toggleTarget?.active ? 'Deactivate Product?' : 'Activate Product?'}
        message={
          toggleTarget?.active
            ? `"${toggleTarget?.name}" will no longer be available for new orders. You can reactivate it anytime.`
            : `"${toggleTarget?.name}" will become available for new orders again.`
        }
        confirmLabel={toggleTarget?.active ? 'Deactivate' : 'Activate'}
        destructive={toggleTarget?.active}
        loading={toggleActiveMutation.isPending}
        onConfirm={() => toggleTarget && toggleActiveMutation.mutate(toggleTarget)}
        onClose={() => setToggleTarget(null)}
      />
    </Box>
  )
}
