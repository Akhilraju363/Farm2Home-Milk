import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Avatar, Divider, IconButton, Alert,
  List, ListItem, ListItemText, ListItemSecondaryAction, Menu, MenuItem, CircularProgress,
} from '@mui/material'
import {
  ArrowBack, Edit, PhotoCamera, Add, MoreVert, Star, StarBorder, Subscriptions as SubscriptionsIcon,
  ShoppingCart, LocationOn, AddCircleOutline, EditCalendar,
} from '@mui/icons-material'
import { useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { EditCustomerDialog } from '../../components/customer/EditCustomerDialog'
import { AddressFormDialog } from '../../components/customer/AddressFormDialog'
import { customerService } from '../../services/customerService'
import { subscriptionService } from '../../services/subscriptionService'
import { orderService } from '../../services/orderService'
import { formatCurrency, formatDate, formatDateTime, statusColor } from '../../utils/formatters'
import { CUSTOMER_STATUS_LABELS } from '../../types/customer.types'
import type { CustomerAddress, CustomerStatus } from '../../types/customer.types'

const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

function Section({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
        {action}
      </Box>
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

export function CustomerDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [editOpen, setEditOpen] = useState(false)
  const [statusTarget, setStatusTarget] = useState<CustomerStatus | null>(null)
  const [addressFormOpen, setAddressFormOpen] = useState(false)
  const [editingAddress, setEditingAddress] = useState<CustomerAddress | null>(null)
  const [deleteAddressTarget, setDeleteAddressTarget] = useState<CustomerAddress | null>(null)
  const [addressMenu, setAddressMenu] = useState<{ anchor: HTMLElement; address: CustomerAddress } | null>(null)
  const [imageError, setImageError] = useState('')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['customers', id],
    queryFn: () => customerService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const customer = data?.data.data

  const { data: addressesRes, isLoading: addressesLoading } = useQuery({
    queryKey: ['customers', id, 'addresses'],
    queryFn: () => customerService.getAddresses(id!),
    enabled: Boolean(id) && Boolean(customer),
  })
  const addresses = addressesRes?.data.data ?? []

  const { data: subscriptionsRes, isLoading: subscriptionsLoading } = useQuery({
    queryKey: ['subscriptions', 'by-customer', id],
    queryFn: () => subscriptionService.getByCustomer(id!, 5),
    enabled: Boolean(id) && Boolean(customer),
  })
  const subscriptions = subscriptionsRes?.data.data.content ?? []

  const { data: ordersRes, isLoading: ordersLoading } = useQuery({
    queryKey: ['orders', 'by-customer', id],
    queryFn: () => orderService.getByCustomer(id!, 5),
    enabled: Boolean(id) && Boolean(customer),
  })
  const orders = ordersRes?.data.data.content ?? []

  const invalidateCustomer = () => queryClient.invalidateQueries({ queryKey: ['customers', id] })
  const invalidateAddresses = () => queryClient.invalidateQueries({ queryKey: ['customers', id, 'addresses'] })

  const statusMutation = useMutation({
    mutationFn: (status: CustomerStatus) => customerService.update(id!, { status }),
    onSuccess: (_res, status) => {
      enqueueSnackbar(`Customer marked ${CUSTOMER_STATUS_LABELS[status].toLowerCase()}`, { variant: 'success' })
      invalidateCustomer()
      setStatusTarget(null)
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? 'Could not update the customer. Please try again.', { variant: 'error' })
      setStatusTarget(null)
    },
  })

  const imageMutation = useMutation({
    mutationFn: (file: File) => customerService.uploadProfileImage(id!, file),
    onSuccess: () => {
      enqueueSnackbar('Profile image updated successfully', { variant: 'success' })
      invalidateCustomer()
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? 'Upload failed. Please try again.', { variant: 'error' })
    },
  })

  const setDefaultMutation = useMutation({
    mutationFn: (addressId: string) => customerService.setDefaultAddress(id!, addressId),
    onSuccess: () => { enqueueSnackbar('Default address updated', { variant: 'success' }); invalidateAddresses() },
    onError: (err: any) => enqueueSnackbar(err.response?.data?.message ?? 'Could not set default address.', { variant: 'error' }),
  })

  const deleteAddressMutation = useMutation({
    mutationFn: (addressId: string) => customerService.deleteAddress(id!, addressId),
    onSuccess: () => {
      enqueueSnackbar('Address deleted successfully', { variant: 'success' })
      invalidateAddresses()
      setDeleteAddressTarget(null)
    },
    onError: (err: any) => {
      enqueueSnackbar(err.response?.data?.message ?? 'Could not delete this address.', { variant: 'error' })
      setDeleteAddressTarget(null)
    },
  })

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
    imageMutation.mutate(file)
  }

  const openAddAddress = () => { setEditingAddress(null); setAddressFormOpen(true) }
  const openEditAddress = (a: CustomerAddress) => { setEditingAddress(a); setAddressFormOpen(true) }
  const handleAddressSaved = () => { setAddressFormOpen(false); invalidateAddresses() }

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

  if (isError || !customer) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/customers')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This customer doesn't exist, was removed, or you don't have access to it." : "Couldn't load this customer. Please try again."}
        </Alert>
      </Box>
    )
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/customers')}><ArrowBack /></IconButton>
          <Avatar src={customer.profileImageUrl ?? undefined} sx={{ width: 56, height: 56, bgcolor: 'primary.main', fontSize: 20 }}>
            {customer.firstName[0]}{customer.lastName[0]}
          </Avatar>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>{customer.firstName} {customer.lastName}</Typography>
              <Chip
                label={CUSTOMER_STATUS_LABELS[customer.status]} size="small"
                color={customer.status === 'ACTIVE' ? 'success' : customer.status === 'SUSPENDED' ? 'error' : 'default'}
              />
            </Box>
            <Typography variant="body2" color="text.secondary">{customer.customerCode}</Typography>
          </Box>
        </Box>
        <Button variant="contained" startIcon={<Edit />} onClick={() => setEditOpen(true)}>Edit</Button>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Overview">
            <Field label="Full Name" value={`${customer.firstName} ${customer.lastName}`} />
            <Field label="Customer ID" value={customer.customerCode} />
            <Field label="Registered On" value={formatDate(customer.createdAt)} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Contact Information">
            <Field label="Mobile Number" value={customer.mobile} />
            <Field label="Email" value={customer.email || 'Not provided'} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Profile Image">
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar src={customer.profileImageUrl ?? undefined} sx={{ width: 72, height: 72, bgcolor: 'action.hover' }}>
                {customer.firstName[0]}{customer.lastName[0]}
              </Avatar>
              <Box>
                <Button
                  size="small" variant="outlined"
                  startIcon={imageMutation.isPending ? <CircularProgress size={16} /> : <PhotoCamera />}
                  onClick={() => fileInputRef.current?.click()}
                  disabled={imageMutation.isPending}
                >
                  {customer.profileImageUrl ? 'Replace Image' : 'Upload Image'}
                </Button>
                <input ref={fileInputRef} type="file" accept={ACCEPTED_IMAGE_TYPES.join(',')} hidden onChange={handleFileSelect} />
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                  JPEG, PNG, or WEBP. Max 5 MB.
                </Typography>
                {imageError && <Typography variant="caption" color="error" display="block">{imageError}</Typography>}
              </Box>
            </Box>
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Account Status">
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Current status: <strong>{CUSTOMER_STATUS_LABELS[customer.status]}</strong>
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {customer.status !== 'ACTIVE' && (
                <Button size="small" variant="outlined" color="success" onClick={() => setStatusTarget('ACTIVE')}>Activate</Button>
              )}
              {customer.status !== 'SUSPENDED' && (
                <Button size="small" variant="outlined" color="error" onClick={() => setStatusTarget('SUSPENDED')}>Suspend</Button>
              )}
              {customer.status !== 'INACTIVE' && (
                <Button size="small" variant="outlined" onClick={() => setStatusTarget('INACTIVE')}>Deactivate</Button>
              )}
            </Box>
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section
            title="Addresses"
            action={<Button size="small" startIcon={<Add />} onClick={openAddAddress}>Add Address</Button>}
          >
            {addressesLoading ? (
              <Skeleton variant="rectangular" height={80} sx={{ borderRadius: 1 }} />
            ) : addresses.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No saved addresses.</Typography>
            ) : (
              <List disablePadding>
                {addresses.map((a) => (
                  <ListItem key={a.id} disableGutters divider sx={{ alignItems: 'flex-start' }}>
                    <LocationOn fontSize="small" color="action" sx={{ mt: 0.5, mr: 1 }} />
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body2" fontWeight={600}>{a.addressLine1}{a.addressLine2 ? `, ${a.addressLine2}` : ''}</Typography>
                          {a.defaultAddress && <Chip label="Default" size="small" color="primary" sx={{ height: 18, fontSize: 10 }} />}
                        </Box>
                      }
                      secondary={`${a.city}, ${a.state} - ${a.pincode}`}
                    />
                    <ListItemSecondaryAction>
                      <IconButton size="small" onClick={(e) => setAddressMenu({ anchor: e.currentTarget, address: a })}>
                        <MoreVert fontSize="small" />
                      </IconButton>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Subscriptions">
            {subscriptionsLoading ? (
              <Skeleton variant="rectangular" height={80} sx={{ borderRadius: 1 }} />
            ) : subscriptions.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No active subscriptions.</Typography>
            ) : (
              <List disablePadding>
                {subscriptions.map((s) => (
                  <ListItem key={s.id} disableGutters divider>
                    <SubscriptionsIcon fontSize="small" color="action" sx={{ mr: 1 }} />
                    <ListItemText
                      primary={`${s.milkType.replace('_', ' ')} • ${s.quantity} L • ${s.scheduleType}`}
                      secondary={`Since ${formatDate(s.startDate)}`}
                    />
                    <Chip label={s.status} size="small" color={statusColor(s.status)} sx={{ fontSize: 11 }} />
                  </ListItem>
                ))}
              </List>
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Orders">
            {ordersLoading ? (
              <Skeleton variant="rectangular" height={80} sx={{ borderRadius: 1 }} />
            ) : orders.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No orders found.</Typography>
            ) : (
              <List disablePadding>
                {orders.map((o) => (
                  <ListItem key={o.id} disableGutters divider>
                    <ShoppingCart fontSize="small" color="action" sx={{ mr: 1 }} />
                    <ListItemText primary={o.orderNumber} secondary={`${formatCurrency(o.totalAmount)} • ${formatDate(o.orderDate)}`} />
                    <Chip label={o.status} size="small" color={statusColor(o.status)} sx={{ fontSize: 11 }} />
                  </ListItem>
                ))}
              </List>
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Activity">
            <Box sx={{ display: 'flex', gap: 1.5, mb: 1.5 }}>
              <AddCircleOutline fontSize="small" color="action" sx={{ mt: 0.25 }} />
              <Box>
                <Typography variant="body2">Account created</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(customer.createdAt)}</Typography>
              </Box>
            </Box>
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              <EditCalendar fontSize="small" color="action" sx={{ mt: 0.25 }} />
              <Box>
                <Typography variant="body2">Current status: {CUSTOMER_STATUS_LABELS[customer.status]}</Typography>
              </Box>
            </Box>
          </Section>
        </Grid>
      </Grid>

      <Menu anchorEl={addressMenu?.anchor} open={Boolean(addressMenu)} onClose={() => setAddressMenu(null)}>
        <MenuItem onClick={() => { if (addressMenu) openEditAddress(addressMenu.address); setAddressMenu(null) }}>
          <Edit fontSize="small" sx={{ mr: 1 }} /> Edit
        </MenuItem>
        {addressMenu && !addressMenu.address.defaultAddress && (
          <MenuItem onClick={() => { setDefaultMutation.mutate(addressMenu.address.id); setAddressMenu(null) }}>
            <Star fontSize="small" sx={{ mr: 1 }} /> Set as Default
          </MenuItem>
        )}
        {addressMenu && addressMenu.address.defaultAddress && (
          <MenuItem disabled>
            <StarBorder fontSize="small" sx={{ mr: 1 }} /> Already Default
          </MenuItem>
        )}
        <MenuItem onClick={() => { if (addressMenu) setDeleteAddressTarget(addressMenu.address); setAddressMenu(null) }} sx={{ color: 'error.main' }}>
          Delete
        </MenuItem>
      </Menu>

      <EditCustomerDialog
        open={editOpen} customer={customer} onClose={() => setEditOpen(false)}
        onSaved={() => { setEditOpen(false); invalidateCustomer() }}
      />

      <AddressFormDialog
        open={addressFormOpen} customerId={id!} address={editingAddress}
        onClose={() => setAddressFormOpen(false)} onSaved={handleAddressSaved}
      />

      <ConfirmDialog
        open={Boolean(statusTarget)}
        title={`${statusTarget === 'ACTIVE' ? 'Activate' : statusTarget === 'SUSPENDED' ? 'Suspend' : 'Deactivate'} Customer?`}
        message={`This customer will be marked ${statusTarget ? CUSTOMER_STATUS_LABELS[statusTarget].toLowerCase() : ''}.`}
        confirmLabel={statusTarget === 'ACTIVE' ? 'Activate' : statusTarget === 'SUSPENDED' ? 'Suspend' : 'Deactivate'}
        destructive={statusTarget !== 'ACTIVE'}
        loading={statusMutation.isPending}
        onConfirm={() => statusTarget && statusMutation.mutate(statusTarget)}
        onClose={() => setStatusTarget(null)}
      />

      <ConfirmDialog
        open={Boolean(deleteAddressTarget)}
        title="Delete Address?"
        message="This address will be permanently removed from the customer's account."
        confirmLabel="Delete"
        destructive
        loading={deleteAddressMutation.isPending}
        onConfirm={() => deleteAddressTarget && deleteAddressMutation.mutate(deleteAddressTarget.id)}
        onClose={() => setDeleteAddressTarget(null)}
      />
    </Box>
  )
}
