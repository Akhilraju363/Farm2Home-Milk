import { Box, Button, IconButton, Chip, Menu, MenuItem, ListItemIcon, ListItemText, Tooltip, Paper, Typography } from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { ArrowBack, Add, Refresh, MoreVert, Edit, ToggleOff, ToggleOn, Delete, Category as CategoryIcon } from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { CategoryFormDialog } from '../../components/product/CategoryFormDialog'
import { useAuth } from '../../hooks/useAuth'
import { productCategoryService } from '../../services/productCategoryService'
import { formatDate } from '../../utils/formatters'
import type { ProductCategory } from '../../types/product.types'

const PAGE_SIZE = 20

export function CategoriesPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const canWrite = user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER') ?? false

  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [editingCategory, setEditingCategory] = useState<ProductCategory | null>(null)
  const [toggleTarget, setToggleTarget] = useState<ProductCategory | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ProductCategory | null>(null)
  const [rowMenu, setRowMenu] = useState<{ anchor: HTMLElement; category: ProductCategory } | null>(null)

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['product-categories', 'all', page],
    queryFn: () => productCategoryService.getAll({ activeOnly: false, page, size: PAGE_SIZE }),
  })
  const categories = data?.data.data.content ?? []
  const totalElements = data?.data.data.totalElements ?? 0

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['product-categories'] })

  const toggleActiveMutation = useMutation({
    mutationFn: (c: ProductCategory) => productCategoryService.update(c.id, { active: !c.active }),
    onSuccess: (_res, c) => {
      enqueueSnackbar(c.active ? 'Category deactivated' : 'Category activated', { variant: 'success' })
      invalidate()
      setToggleTarget(null)
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? 'Could not update the category. Please try again.', { variant: 'error' })
      setToggleTarget(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (c: ProductCategory) => productCategoryService.delete(c.id),
    onSuccess: () => {
      enqueueSnackbar('Category deleted successfully', { variant: 'success' })
      invalidate()
      setDeleteTarget(null)
    },
    onError: (err: any) => {
      // The backend returns a 409 with a specific message when the category is still assigned to
      // live products (see ProductCategoryServiceImpl.delete) - surfaced verbatim rather than a
      // generic failure message, since it tells the admin exactly what to do next.
      enqueueSnackbar(err.response?.data?.message ?? "Couldn't delete this category. Please try again.", { variant: 'error' })
      setDeleteTarget(null)
    },
  })

  const openAdd = () => { setEditingCategory(null); setFormOpen(true) }
  const openEdit = (c: ProductCategory) => { setEditingCategory(c); setFormOpen(true) }
  const handleSaved = () => { setFormOpen(false); invalidate() }

  const columns: GridColDef<ProductCategory>[] = [
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 160 },
    {
      field: 'description', headerName: 'Description', flex: 1.5, minWidth: 200,
      renderCell: ({ value }) => value || <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    {
      field: 'active', headerName: 'Status', width: 120,
      renderCell: ({ value }) => <Chip label={value ? 'Active' : 'Inactive'} size="small" color={value ? 'success' : 'default'} />,
    },
    {
      field: 'createdAt', headerName: 'Created Date', width: 140,
      renderCell: ({ value }) => formatDate(value as string),
    },
    {
      field: 'actions', headerName: '', width: 60, sortable: false, filterable: false,
      renderCell: ({ row }) => canWrite ? (
        <IconButton size="small" onClick={(e) => setRowMenu({ anchor: e.currentTarget, category: row })}>
          <MoreVert fontSize="small" />
        </IconButton>
      ) : null,
    },
  ]

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
        <IconButton onClick={() => navigate('/products')} size="small"><ArrowBack /></IconButton>
        <Typography variant="body2" color="text.secondary">Back to Products</Typography>
      </Box>

      <PageHeader
        title="Category Management"
        subtitle="Organize the product catalog into categories customers can browse."
        action={canWrite ? { label: 'Add Category', icon: <Add />, onClick: openAdd } : undefined}
      />

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()} size="small"><Refresh /></IconButton>
        </Tooltip>
      </Box>

      {!isLoading && categories.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', borderRadius: 2 }}>
          <CategoryIcon sx={{ fontSize: 44, color: 'text.disabled', mb: 1.5 }} />
          <Typography variant="h6" fontWeight={700} gutterBottom>No categories have been added yet.</Typography>
          {canWrite && <Button variant="contained" startIcon={<Add />} onClick={openAdd} sx={{ mt: 1 }}>Add Category</Button>}
        </Paper>
      ) : (
        <DataGrid
          rows={categories}
          columns={columns}
          loading={isLoading || isFetching}
          rowCount={totalElements}
          paginationMode="server"
          paginationModel={{ page, pageSize: PAGE_SIZE }}
          onPaginationModelChange={({ page: p }) => setPage(p)}
          pageSizeOptions={[PAGE_SIZE]}
          autoHeight
          disableRowSelectionOnClick
          sx={{ bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
        />
      )}

      <Menu anchorEl={rowMenu?.anchor} open={Boolean(rowMenu)} onClose={() => setRowMenu(null)}>
        <MenuItem onClick={() => { if (rowMenu) openEdit(rowMenu.category); setRowMenu(null) }}>
          <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
          <ListItemText>Edit</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { if (rowMenu) setToggleTarget(rowMenu.category); setRowMenu(null) }}>
          <ListItemIcon>{rowMenu?.category.active ? <ToggleOff fontSize="small" /> : <ToggleOn fontSize="small" />}</ListItemIcon>
          <ListItemText>{rowMenu?.category.active ? 'Deactivate' : 'Activate'}</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { if (rowMenu) setDeleteTarget(rowMenu.category); setRowMenu(null) }} sx={{ color: 'error.main' }}>
          <ListItemIcon><Delete fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Delete</ListItemText>
        </MenuItem>
      </Menu>

      <CategoryFormDialog open={formOpen} category={editingCategory} onClose={() => setFormOpen(false)} onSaved={handleSaved} />

      <ConfirmDialog
        open={Boolean(toggleTarget)}
        title={toggleTarget?.active ? 'Deactivate Category?' : 'Activate Category?'}
        message={
          toggleTarget?.active
            ? `"${toggleTarget?.name}" will no longer be selectable for new or updated products.`
            : `"${toggleTarget?.name}" will become selectable for products again.`
        }
        confirmLabel={toggleTarget?.active ? 'Deactivate' : 'Activate'}
        destructive={toggleTarget?.active}
        loading={toggleActiveMutation.isPending}
        onConfirm={() => toggleTarget && toggleActiveMutation.mutate(toggleTarget)}
        onClose={() => setToggleTarget(null)}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete Category?"
        message={`"${deleteTarget?.name}" will be permanently removed. This cannot be undone.`}
        confirmLabel="Delete"
        destructive
        loading={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
