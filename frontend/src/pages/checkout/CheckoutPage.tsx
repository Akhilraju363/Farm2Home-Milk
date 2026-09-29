import {
  Box, Paper, Typography, Button, Divider, List, ListItem, ListItemText, Alert,
  CircularProgress, Skeleton, Chip, TextField, Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material'
import { LocationOn, CheckCircle, HelpOutline, ArrowBack } from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import dayjs from 'dayjs'
import { PageHeader } from '../../components/common/PageHeader'
import { DeliveryAddressDialog } from '../../components/product/DeliveryAddressDialog'
import { useAuth } from '../../hooks/useAuth'
import { cartService } from '../../services/cartService'
import { orderService } from '../../services/orderService'
import { customerService } from '../../services/customerService'
import { paymentService } from '../../services/paymentService'
import { formatCurrency } from '../../utils/formatters'
import { completeOnlinePayment } from '../../utils/onlinePayment'
import type { OnlinePaymentOutcome } from '../../utils/onlinePayment'
import type { Payment } from '../../types/payment.types'
import { PaymentMethodSelector } from '../../components/payment/PaymentMethodSelector'
import type { CustomerPaymentMethod } from '../../components/payment/PaymentMethodSelector'
import { PaymentResultView } from '../../components/payment/PaymentResultView'

/** Places a real order from the caller's own server-side Cart via POST /orders/checkout - see
 *  order-service's OrderServiceImpl.checkout(). Items/prices are never sent from here (the backend
 *  re-resolves and re-validates everything: price, stock, delivery eligibility - same pipeline as
 *  BuyNowDialog's single-item path, see priceItem()/verifyDeliveryEligibility()), so this page is
 *  just a summary + confirmation step, not a second source of truth.
 *
 *  The chosen payment method is collected right after the order is created, through the same
 *  POST /payments call PayNowDialog uses, for the order's own server-computed total: Cash on
 *  Delivery records a PENDING cash payment, Wallet debits immediately, and Online opens Razorpay
 *  Checkout (see completeOnlinePayment - the outcome is always confirmed by the backend). If the
 *  payment can't be completed the order still exists and can be paid from its details page. */
export function CheckoutPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const { enqueueSnackbar } = useSnackbar()

  const [orderDate, setOrderDate] = useState(dayjs().format('YYYY-MM-DD'))
  const [addressDialogOpen, setAddressDialogOpen] = useState(false)
  const [serverError, setServerError] = useState('')
  const [paymentMethod, setPaymentMethod] = useState<CustomerPaymentMethod>('RAZORPAY')
  const [placing, setPlacing] = useState(false)
  const [onlineResult, setOnlineResult] = useState<{
    orderId: string; payment: Payment; outcome: OnlinePaymentOutcome; message?: string
  } | null>(null)

  const { data: cartRes, isLoading: cartLoading, isError: cartError } = useQuery({
    queryKey: ['cart'],
    queryFn: () => cartService.getCart(),
  })
  const cart = cartRes?.data.data
  const items = cart?.items ?? []
  const purchasableItems = items.filter((i) => i.available)
  const hasUnavailable = items.some((i) => !i.available)

  const { data: addressesRes } = useQuery({
    queryKey: ['customers', user?.id, 'addresses'],
    queryFn: () => customerService.getAddresses(user!.id),
    enabled: Boolean(user?.id),
  })
  const defaultAddress = addressesRes?.data.data.find((a) => a.defaultAddress)

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

  const placeOrder = async () => {
    setServerError('')
    setPlacing(true)
    let order
    try {
      order = (await orderService.checkout({ orderDate })).data.data
    } catch (err: any) {
      setServerError(err.response?.data?.message ?? 'Could not place the order. Please try again.')
      setPlacing(false)
      return
    }
    queryClient.invalidateQueries({ queryKey: ['cart'] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })

    try {
      const payment = (await paymentService.initiate({
        orderId: order.id, amount: order.totalAmount, paymentMethod,
      })).data.data
      queryClient.invalidateQueries({ queryKey: ['payments'] })

      if (paymentMethod === 'RAZORPAY') {
        const result = await completeOnlinePayment(payment, {
          description: `Order ${order.orderNumber}`,
          prefill: { contact: user?.mobile, email: user?.email },
        })
        queryClient.invalidateQueries({ queryKey: ['payments'] })
        setOnlineResult({ orderId: order.id, ...result })
        return
      }
      if (paymentMethod === 'WALLET') {
        queryClient.invalidateQueries({ queryKey: ['wallet'] })
        enqueueSnackbar('Order placed and paid from your wallet', { variant: 'success' })
      } else {
        enqueueSnackbar('Order placed - pay in cash on delivery', { variant: 'success' })
      }
      navigate(`/orders/${order.id}`)
    } catch (err: any) {
      enqueueSnackbar(
        `Order placed, but the payment couldn't be completed: ${err.response?.data?.message ?? err.message ?? 'please try again'}. You can pay from the order page.`,
        { variant: 'warning' },
      )
      navigate(`/orders/${order.id}`)
    } finally {
      setPlacing(false)
    }
  }

  const knownOutOfRadius = availability?.deliveryAvailable === false
  const canPlaceOrder = purchasableItems.length > 0 && !knownOutOfRadius && !placing

  if (cartLoading) {
    return (
      <Box>
        <Skeleton width={160} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={320} sx={{ borderRadius: 2 }} />
      </Box>
    )
  }

  if (cartError) {
    return <Alert severity="error">Couldn't load your cart. Please try again.</Alert>
  }

  if (items.length === 0) {
    return (
      <Box>
        <PageHeader title="Checkout" />
        <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => navigate('/products')}>Go to Shop</Button>}>
          Your cart is empty.
        </Alert>
      </Box>
    )
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/cart')}>Back to Cart</Button>
      </Box>
      <PageHeader title="Checkout" />

      {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
      {hasUnavailable && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Some items in your cart are unavailable and will not be included in this order.
        </Alert>
      )}

      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
        <Box sx={{ flex: 2, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5, flexWrap: 'wrap' }}>
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
            </Box>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 2 }}>
            <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Delivery Date</Typography>
            <TextField
              type="date" size="small" fullWidth value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              inputProps={{ min: dayjs().format('YYYY-MM-DD') }}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 2 }}>
            <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Payment Method</Typography>
            <PaymentMethodSelector value={paymentMethod} onChange={setPaymentMethod} disabled={placing} />
          </Paper>

          <Paper variant="outlined" sx={{ borderRadius: 2 }}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ p: 2.5, pb: 1.5 }}>Items</Typography>
            <List disablePadding>
              {items.map((item, idx) => (
                <ListItem key={item.id} divider={idx < items.length - 1} sx={{ opacity: item.available ? 1 : 0.6 }}>
                  <ListItemText
                    primary={item.productName ?? 'Product'}
                    secondary={item.available ? `${item.quantity} ${item.unit ?? ''} × ${formatCurrency(item.unitPrice)}` : (item.unavailableReason ?? 'Unavailable')}
                  />
                  <Typography variant="body2" fontWeight={600}>
                    {item.available ? formatCurrency(item.subtotal) : '—'}
                  </Typography>
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
              <Typography variant="body2" fontWeight={600}>{formatCurrency(cart?.subtotal)}</Typography>
            </Box>
            <Divider sx={{ my: 1.5 }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
              <Typography variant="subtitle1" fontWeight={700}>{formatCurrency(cart?.subtotal)}</Typography>
            </Box>
            <Button
              variant="contained" fullWidth size="large"
              disabled={!canPlaceOrder}
              onClick={placeOrder}
            >
              {placing ? <CircularProgress size={20} color="inherit" />
                : paymentMethod === 'RAZORPAY' ? 'Place Order & Pay' : 'Place Order'}
            </Button>
            {knownOutOfRadius && (
              <Typography variant="caption" color="error" display="block" sx={{ mt: 1 }}>
                Delivery isn't available to your current address.
              </Typography>
            )}
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1.5 }}>
              {paymentMethod === 'RAZORPAY'
                ? 'You\'ll pay securely with Razorpay right after placing the order.'
                : paymentMethod === 'CASH'
                  ? 'Pay the delivery partner in cash when your order arrives.'
                  : 'The order total will be debited from your wallet.'}
            </Typography>
          </Paper>
        </Box>
      </Box>

      <Dialog open={Boolean(onlineResult)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Order Placed</DialogTitle>
        <DialogContent>
          {onlineResult && (
            <PaymentResultView payment={onlineResult.payment} outcome={onlineResult.outcome} message={onlineResult.message} />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button variant="contained" fullWidth onClick={() => onlineResult && navigate(`/orders/${onlineResult.orderId}`)}>
            View Order
          </Button>
        </DialogActions>
      </Dialog>

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
