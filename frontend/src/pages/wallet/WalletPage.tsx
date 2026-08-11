import {
  Box, Paper, Typography, TextField, Button, CircularProgress, Alert, List, ListItem,
  ListItemText, Skeleton, Pagination, Grid,
} from '@mui/material'
import { AccountBalanceWallet, Add, ArrowUpward, ArrowDownward } from '@mui/icons-material'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSnackbar } from 'notistack'
import { walletService } from '../../services/walletService'
import { formatCurrency, formatDateTime } from '../../utils/formatters'

const PAGE_SIZE = 10

export function WalletPage() {
  const { enqueueSnackbar } = useSnackbar()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [serverError, setServerError] = useState('')

  const { data: walletData, isLoading: walletLoading } = useQuery({
    queryKey: ['wallet', 'me'],
    queryFn: () => walletService.getMyWallet(),
  })
  const wallet = walletData?.data.data

  const { data: txData, isLoading: txLoading, isFetching: txFetching } = useQuery({
    queryKey: ['wallet', 'transactions', page],
    queryFn: () => walletService.getTransactions({ page, size: PAGE_SIZE }),
  })
  const transactions = txData?.data.data.content ?? []
  const totalElements = txData?.data.data.totalElements ?? 0
  const totalPages = txData?.data.data.totalPages ?? 0

  const topUpMutation = useMutation({
    mutationFn: () => walletService.topUp({ amount: Number(amount), description: description || undefined }),
    onSuccess: () => {
      enqueueSnackbar('Wallet topped up successfully', { variant: 'success' })
      setAmount(''); setDescription(''); setServerError('')
      queryClient.invalidateQueries({ queryKey: ['wallet'] })
    },
    onError: (err: any) => setServerError(err.response?.data?.message ?? 'Could not top up your wallet. Please try again.'),
  })

  const handleTopUp = () => {
    setServerError('')
    const value = Number(amount)
    if (!amount || Number.isNaN(value) || value < 1) {
      setFieldError('Minimum top-up amount is ₹1.00')
      return
    }
    setFieldError('')
    topUpMutation.mutate()
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={0.5}>My Wallet</Typography>
      <Typography variant="body2" color="text.secondary" mb={3}>Your Farm2Home in-app wallet.</Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
              <AccountBalanceWallet color="primary" />
              <Typography variant="subtitle1" fontWeight={700}>Current Balance</Typography>
            </Box>
            {walletLoading ? (
              <Skeleton width={140} height={48} />
            ) : (
              <Typography variant="h4" fontWeight={700}>{formatCurrency(wallet?.balance ?? 0)}</Typography>
            )}
          </Paper>

          <Paper variant="outlined" sx={{ p: 3, borderRadius: 2 }}>
            <Typography variant="subtitle1" fontWeight={700} mb={2}>Top Up Wallet</Typography>
            {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
            <TextField
              label="Amount" type="number" fullWidth size="small" sx={{ mb: 2 }}
              inputProps={{ min: 1, step: '0.01' }}
              error={!!fieldError} helperText={fieldError || 'Minimum ₹1.00'}
              value={amount} onChange={(e) => setAmount(e.target.value)}
            />
            <TextField
              label="Description (optional)" fullWidth size="small" sx={{ mb: 2 }}
              value={description} onChange={(e) => setDescription(e.target.value)}
            />
            <Button
              variant="contained" fullWidth startIcon={topUpMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <Add />}
              onClick={handleTopUp} disabled={topUpMutation.isPending}
            >
              Top Up
            </Button>
          </Paper>
        </Grid>

        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ p: 3, borderRadius: 2 }}>
            <Typography variant="subtitle1" fontWeight={700} mb={2}>Transaction History</Typography>
            {txLoading ? (
              <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 1 }} />
            ) : transactions.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                No transactions yet.
              </Typography>
            ) : (
              <>
                <List disablePadding sx={{ opacity: txFetching ? 0.6 : 1 }}>
                  {transactions.map((t) => (
                    <ListItem key={t.id} disableGutters divider>
                      {t.transactionType === 'CREDIT'
                        ? <ArrowUpward fontSize="small" color="success" sx={{ mr: 1.5 }} />
                        : <ArrowDownward fontSize="small" color="error" sx={{ mr: 1.5 }} />}
                      <ListItemText
                        primary={t.description || (t.transactionType === 'CREDIT' ? 'Credit' : 'Debit')}
                        secondary={formatDateTime(t.createdAt)}
                      />
                      <Typography variant="body2" fontWeight={700} color={t.transactionType === 'CREDIT' ? 'success.main' : 'error.main'}>
                        {t.transactionType === 'CREDIT' ? '+' : '-'}{formatCurrency(t.amount)}
                      </Typography>
                    </ListItem>
                  ))}
                </List>
                {totalPages > 1 && (
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      {totalElements.toLocaleString()} transaction{totalElements === 1 ? '' : 's'}
                    </Typography>
                    <Pagination count={totalPages} page={page + 1} onChange={(_e, p) => setPage(p - 1)} color="primary" size="small" />
                  </Box>
                )}
              </>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}
