import {
  Box, Paper, Typography, Chip, Button, Grid, Skeleton, Divider, IconButton, Alert, List,
  ListItem, ListItemText, CircularProgress,
} from '@mui/material'
import { ArrowBack, Download, Agriculture, Person, LocalShipping } from '@mui/icons-material'
import { useState } from 'react'
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { invoiceService } from '../../services/invoiceService'
import { formatCurrency, formatDate, formatDateTime, statusColor } from '../../utils/formatters'
import { downloadBlob } from '../../utils/download'
import type { ReactNode } from 'react'

const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending', ASSIGNED: 'Assigned', OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED: 'Delivered', CANCELLED: 'Cancelled',
}

const PAYMENT_METHOD_LABELS: Record<string, string> = {
  UPI: 'UPI', RAZORPAY: 'Razorpay', WALLET: 'Wallet', CASH: 'Cash',
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, height: '100%' }}>
      <Typography variant="subtitle1" fontWeight={700} mb={1}>{title}</Typography>
      <Divider sx={{ mb: 2 }} />
      {children}
    </Paper>
  )
}

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
      <Typography variant="body2" fontWeight={500}>{value}</Typography>
    </Box>
  )
}

export function InvoiceDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const [downloading, setDownloading] = useState(false)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['invoices', id],
    queryFn: () => invoiceService.getById(id!),
    enabled: Boolean(id),
    retry: false,
  })
  const invoice = data?.data.data

  const handleDownload = async () => {
    if (!id) return
    setDownloading(true)
    try {
      const { blob, filename } = await invoiceService.downloadPdf(id)
      downloadBlob(blob, filename)
    } catch {
      enqueueSnackbar("Couldn't download the invoice PDF. Please try again.", { variant: 'error' })
    } finally {
      setDownloading(false)
    }
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

  if (isError || !invoice) {
    const status = (error as any)?.response?.status
    return (
      <Box>
        <IconButton onClick={() => navigate('/invoices')} sx={{ mb: 2 }}><ArrowBack /></IconButton>
        <Alert severity={status === 404 ? 'warning' : 'error'}>
          {status === 404 ? "This invoice doesn't exist or you don't have access to it." : "Couldn't load this invoice. Please try again."}
        </Alert>
      </Box>
    )
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate(-1)}><ArrowBack /></IconButton>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h5" fontWeight={700}>{invoice.invoiceNumber}</Typography>
              {invoice.orderStatus && (
                <Chip label={ORDER_STATUS_LABELS[invoice.orderStatus] ?? invoice.orderStatus} size="small" color={statusColor(invoice.orderStatus)} />
              )}
            </Box>
            <Typography variant="body2" color="text.secondary">
              Issued {formatDate(invoice.issueDate)} • {formatCurrency(invoice.totalAmount)}
            </Typography>
          </Box>
        </Box>
        <Button
          variant="contained" startIcon={downloading ? <CircularProgress size={16} color="inherit" /> : <Download />}
          onClick={handleDownload} disabled={downloading}
        >
          Download PDF
        </Button>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Section title="Farm / Business Information">
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Agriculture color="primary" />
              <Typography variant="body2" fontWeight={600}>Farm2Home Milk</Typography>
            </Box>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
              support@farm2homemilk.example
            </Typography>
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Customer Information">
            {invoice.customerName ? (
              <>
                <Field label="Name" value={invoice.customerName} />
                {invoice.customerMobile && <Field label="Mobile" value={invoice.customerMobile} />}
                {invoice.customerEmail && <Field label="Email" value={invoice.customerEmail} />}
                {invoice.billingAddress?.addressLine1 && (
                  <Field
                    label="Address"
                    value={[
                      invoice.billingAddress.addressLine1, invoice.billingAddress.addressLine2,
                      invoice.billingAddress.city, invoice.billingAddress.state, invoice.billingAddress.pincode,
                    ].filter(Boolean).join(', ')}
                  />
                )}
              </>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
                <Person fontSize="small" />
                <Typography variant="body2">Customer details unavailable</Typography>
              </Box>
            )}
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Invoice Details">
            <Field label="Invoice Number" value={invoice.invoiceNumber} />
            <Field
              label="Order Number"
              value={invoice.orderNumber
                ? <RouterLink to={`/orders/${invoice.orderId}`} style={{ color: 'inherit' }}>{invoice.orderNumber}</RouterLink>
                : '—'}
            />
            <Field label="Issue Date" value={formatDate(invoice.issueDate)} />
          </Section>
        </Grid>

        <Grid item xs={12} md={6}>
          <Section title="Payment Information">
            {invoice.payment ? (
              <>
                <Field label="Method" value={PAYMENT_METHOD_LABELS[invoice.payment.paymentMethod ?? ''] ?? invoice.payment.paymentMethod} />
                <Field label="Reference" value={invoice.payment.paymentReference} />
                <Field
                  label="Status"
                  value={<Chip label={invoice.payment.paymentStatus} size="small" color={statusColor(invoice.payment.paymentStatus ?? '')} />}
                />
                {invoice.payment.paidAt && <Field label="Paid At" value={formatDateTime(invoice.payment.paidAt)} />}
              </>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
                <LocalShipping fontSize="small" />
                <Typography variant="body2">No payment recorded yet for this order.</Typography>
              </Box>
            )}
          </Section>
        </Grid>

        <Grid item xs={12}>
          <Section title="Product Details">
            <Box sx={{ overflowX: 'auto' }}>
              <List disablePadding sx={{ minWidth: 480 }}>
                <ListItem disableGutters divider sx={{ pb: 1 }}>
                  <ListItemText primary={<Typography variant="caption" color="text.secondary">Milk Type</Typography>} />
                  <Box sx={{ display: 'flex', gap: 4, minWidth: 320, justifyContent: 'flex-end' }}>
                    <Typography variant="caption" color="text.secondary" sx={{ width: 90, textAlign: 'right' }}>Quantity</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ width: 90, textAlign: 'right' }}>Unit Price</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ width: 90, textAlign: 'right' }}>Subtotal</Typography>
                  </Box>
                </ListItem>
                {invoice.items.length === 0 ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>No line items available.</Typography>
                ) : invoice.items.map((item, idx) => (
                  <ListItem key={idx} disableGutters divider>
                    <ListItemText primary={item.milkType} />
                    <Box sx={{ display: 'flex', gap: 4, minWidth: 320, justifyContent: 'flex-end' }}>
                      <Typography variant="body2" sx={{ width: 90, textAlign: 'right' }}>{item.quantity} L</Typography>
                      <Typography variant="body2" sx={{ width: 90, textAlign: 'right' }}>{formatCurrency(item.unitPrice)}</Typography>
                      <Typography variant="body2" fontWeight={600} sx={{ width: 90, textAlign: 'right' }}>{formatCurrency(item.totalPrice)}</Typography>
                    </Box>
                  </ListItem>
                ))}
              </List>
            </Box>

            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3 }}>
              <Box sx={{ minWidth: 240 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Subtotal</Typography>
                  <Typography variant="body2">{formatCurrency(invoice.subtotal)}</Typography>
                </Box>
                {/* No Tax / Delivery Charge / Discount rows - none exist anywhere in the backend
                    (see the Phase 1 audit), so subtotal and total are always equal. */}
                <Divider sx={{ my: 1 }} />
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="subtitle1" fontWeight={700}>Total Amount</Typography>
                  <Typography variant="subtitle1" fontWeight={700} color="primary.main">{formatCurrency(invoice.totalAmount)}</Typography>
                </Box>
              </Box>
            </Box>
          </Section>
        </Grid>
      </Grid>
    </Box>
  )
}
