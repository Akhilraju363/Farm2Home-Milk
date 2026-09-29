import { Box, Paper, Radio, RadioGroup, Typography } from '@mui/material'
import { AccountBalanceWallet, CreditCard, LocalAtm } from '@mui/icons-material'
import type { ReactNode } from 'react'
import type { PaymentMethod } from '../../types/payment.types'

/** The methods a customer can choose. UPI is not listed separately: UPI, UPI apps (Google Pay,
 *  PhonePe, Paytm), cards and netbanking are all offered inside Razorpay Checkout ("Online"). */
export type CustomerPaymentMethod = Extract<PaymentMethod, 'RAZORPAY' | 'WALLET' | 'CASH'>
export const CUSTOMER_PAYMENT_METHODS: CustomerPaymentMethod[] = ['RAZORPAY', 'CASH', 'WALLET']

const OPTIONS: Record<CustomerPaymentMethod, { title: string; description: string; icon: ReactNode }> = {
  RAZORPAY: {
    title: 'Online Payment',
    description: 'Credit/debit card, UPI, Google Pay, PhonePe, Paytm and more - secured by Razorpay.',
    icon: <CreditCard color="action" />,
  },
  CASH: {
    title: 'Cash on Delivery',
    description: 'Pay the delivery partner in cash when your order arrives.',
    icon: <LocalAtm color="action" />,
  },
  WALLET: {
    title: 'Farm2Home Wallet',
    description: 'Pay instantly from your wallet balance.',
    icon: <AccountBalanceWallet color="action" />,
  },
}

interface Props {
  value: CustomerPaymentMethod
  onChange: (method: CustomerPaymentMethod) => void
  methods?: CustomerPaymentMethod[]
  disabled?: boolean
}

export function PaymentMethodSelector({ value, onChange, methods = CUSTOMER_PAYMENT_METHODS, disabled }: Props) {
  return (
    <RadioGroup
      value={value}
      onChange={(e) => onChange(e.target.value as CustomerPaymentMethod)}
      sx={{ gap: 1 }}
    >
      {methods.map((m) => {
        const selected = value === m
        return (
          <Paper
            key={m}
            variant="outlined"
            onClick={() => !disabled && onChange(m)}
            sx={{
              p: 1.5, borderRadius: 2, display: 'flex', alignItems: 'center', gap: 1.5,
              cursor: disabled ? 'default' : 'pointer', opacity: disabled ? 0.7 : 1,
              borderColor: selected ? 'primary.main' : 'divider',
              borderWidth: selected ? 2 : 1,
            }}
          >
            <Radio value={m} disabled={disabled} size="small" sx={{ p: 0.5 }} inputProps={{ 'aria-label': OPTIONS[m].title }} />
            {OPTIONS[m].icon}
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" fontWeight={700}>{OPTIONS[m].title}</Typography>
              <Typography variant="caption" color="text.secondary">{OPTIONS[m].description}</Typography>
            </Box>
          </Paper>
        )
      })}
    </RadioGroup>
  )
}
