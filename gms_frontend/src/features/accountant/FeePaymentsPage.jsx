import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import { Add, Receipt } from '@mui/icons-material';
import { DataGrid } from '@mui/x-data-grid';
import dayjs from 'dayjs';
import { toast } from 'react-toastify';
import {
  useGetFeePaymentsQuery,
  useGetStudentFeesQuery,
  useRecordFeePaymentMutation,
} from '../../store/api/apiSlice';
import { ROUTES } from '../../constants';

const money = (value) =>
  value == null ? '—' : `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

const PAYMENT_METHODS = ['CASH', 'BANK_TRANSFER', 'CARD', 'CHEQUE', 'ONLINE'];

const emptyForm = (studentFeeId = '', amount = '') => ({
  studentFeeId,
  amount,
  paymentDate: dayjs().format('YYYY-MM-DD'),
  paymentMethod: 'CASH',
  paymentReference: `RCPT-${dayjs().format('YYYYMMDDHHmmss')}`,
  remarks: '',
});

const FeePaymentsPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { data: payments = [], isLoading, isError } = useGetFeePaymentsQuery();
  const { data: studentFees = [] } = useGetStudentFeesQuery();
  const [recordPayment, { isLoading: isSaving }] = useRecordFeePaymentMutation();

  const outstandingFees = useMemo(
    () =>
      studentFees.filter(
        (fee) => fee.status !== 'PAID' && Number(fee.balanceAmount ?? 0) > 0,
      ),
    [studentFees],
  );

  const [open, setOpen] = useState(Boolean(location.state?.studentFeeId));
  const [form, setForm] = useState(() => {
    const seeded = location.state?.studentFeeId;
    const fee = seeded ? studentFees.find((f) => f.id === seeded) : null;
    return emptyForm(seeded ?? '', fee?.balanceAmount ?? '');
  });
  const [error, setError] = useState('');

  const selectedFee = outstandingFees.find((fee) => String(fee.id) === String(form.studentFeeId));

  // Arriving from the outstanding list: the fee rows load after this component mounts.
  useEffect(() => {
    const seeded = location.state?.studentFeeId;
    if (!seeded || form.studentFeeId !== String(seeded) || form.amount !== '') return;
    const fee = studentFees.find((row) => row.id === seeded);
    if (fee) setForm((prev) => ({ ...prev, amount: fee.balanceAmount }));
  }, [studentFees, location.state, form.studentFeeId, form.amount]);

  const handleOpen = () => {
    setForm(emptyForm());
    setError('');
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setError('');
  };

  const handleFeeChange = (event) => {
    const fee = outstandingFees.find((row) => String(row.id) === event.target.value);
    setForm((prev) => ({
      ...prev,
      studentFeeId: event.target.value,
      amount: fee?.balanceAmount ?? '',
    }));
  };

  const handleSubmit = async () => {
    setError('');
    const amount = Number(form.amount);
    if (!form.studentFeeId) return setError('Select the student fee being paid.');
    if (!Number.isFinite(amount) || amount <= 0) return setError('Enter an amount greater than zero.');
    if (selectedFee && amount > Number(selectedFee.balanceAmount)) {
      return setError(`Amount exceeds the outstanding balance of ${money(selectedFee.balanceAmount)}.`);
    }
    if (!form.paymentDate || dayjs(form.paymentDate).isAfter(dayjs(), 'day')) {
      return setError('Payment date cannot be in the future.');
    }
    if (!form.paymentReference.trim()) return setError('A payment reference is required.');

    try {
      await recordPayment({
        studentFeeId: Number(form.studentFeeId),
        amount,
        paymentDate: form.paymentDate,
        paymentMethod: form.paymentMethod,
        paymentReference: form.paymentReference.trim(),
        remarks: form.remarks.trim() || undefined,
      }).unwrap();
      toast.success('Payment recorded');
      handleClose();
    } catch (err) {
      setError(err?.data?.message || 'Unable to record the payment.');
    }
  };

  const columns = [
    {
      field: 'paymentDate',
      headerName: 'Date',
      width: 120,
      valueFormatter: ({ value }) => (value ? dayjs(value).format('DD MMM YYYY') : '—'),
    },
    { field: 'studentName', headerName: 'Student', flex: 1, minWidth: 160 },
    { field: 'feeType', headerName: 'Fee', flex: 1, minWidth: 140 },
    {
      field: 'amount',
      headerName: 'Amount',
      width: 130,
      align: 'right',
      headerAlign: 'right',
      valueFormatter: ({ value }) => money(value),
    },
    {
      field: 'paymentMethod',
      headerName: 'Method',
      width: 140,
      valueFormatter: ({ value }) => (value ? value.replace('_', ' ') : '—'),
    },
    { field: 'paymentReference', headerName: 'Reference', flex: 1, minWidth: 160 },
    {
      field: 'status',
      headerName: 'Status',
      width: 120,
      renderCell: ({ value }) => (
        <Chip size="small" label={value || '—'} color={value === 'SUCCESS' ? 'success' : 'default'} />
      ),
    },
  ];

  const totalCollected = payments.reduce((sum, row) => sum + Number(row.amount ?? 0), 0);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Fee Payments
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {payments.length} payment{payments.length === 1 ? '' : 's'} in the last year · {money(totalCollected)} collected
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={handleOpen}>
          Record Payment
        </Button>
      </Box>

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Payments could not be loaded.
        </Alert>
      )}

      <Paper sx={{ width: '100%' }}>
        <DataGrid
          rows={payments.map((row) => ({ ...row, id: row.id }))}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          pageSizeOptions={[10, 25, 50]}
          sx={{ border: 0, '& .MuiDataGrid-columnHeaders': { backgroundColor: '#f5f5f5' } }}
        />
      </Paper>

      <Paper sx={{ p: 2, mt: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
        <Receipt color="action" />
        <Typography variant="body2" color="text.secondary">
          Recorded payments cannot be edited or deleted; corrections require a reversal workflow.
          {outstandingFees.length > 0
            ? ` ${outstandingFees.length} student fee${outstandingFees.length === 1 ? '' : 's'} still owe a balance.`
            : ' Every student fee is fully paid.'}
        </Typography>
        {outstandingFees.length > 0 && (
          <Button size="small" onClick={() => navigate(ROUTES.ACCOUNTANT.OUTSTANDING)}>
            Review balances
          </Button>
        )}
      </Paper>

      <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
        <DialogTitle>Record Payment</DialogTitle>
        <DialogContent>
          {error && (
            <Alert severity="error" sx={{ mt: 1, mb: 1 }}>
              {error}
            </Alert>
          )}
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid item xs={12}>
              <TextField
                select
                fullWidth
                label="Student Fee"
                value={form.studentFeeId}
                onChange={handleFeeChange}
                helperText={
                  selectedFee
                    ? `${selectedFee.studentName} · ${selectedFee.feeType} · balance ${money(selectedFee.balanceAmount)}`
                    : 'Only fees with an outstanding balance are listed'
                }
              >
                {outstandingFees.map((fee) => (
                  <MenuItem key={fee.id} value={String(fee.id)}>
                    {fee.studentName} — {fee.feeType} ({money(fee.balanceAmount)} due)
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type="number"
                label="Amount"
                value={form.amount}
                onChange={(event) => setForm((prev) => ({ ...prev, amount: event.target.value }))}
                inputProps={{ min: 0.01, step: 0.01 }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type="date"
                label="Payment Date"
                value={form.paymentDate}
                onChange={(event) => setForm((prev) => ({ ...prev, paymentDate: event.target.value }))}
                InputLabelProps={{ shrink: true }}
                inputProps={{ max: dayjs().format('YYYY-MM-DD') }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                select
                fullWidth
                label="Method"
                value={form.paymentMethod}
                onChange={(event) => setForm((prev) => ({ ...prev, paymentMethod: event.target.value }))}
              >
                {PAYMENT_METHODS.map((method) => (
                  <MenuItem key={method} value={method}>
                    {method.replace('_', ' ')}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Reference"
                value={form.paymentReference}
                onChange={(event) => setForm((prev) => ({ ...prev, paymentReference: event.target.value }))}
                helperText="Unique per school"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                minRows={2}
                label="Remarks"
                value={form.remarks}
                onChange={(event) => setForm((prev) => ({ ...prev, remarks: event.target.value }))}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>
          <Button variant="contained" onClick={handleSubmit} disabled={isSaving || outstandingFees.length === 0}>
            {isSaving ? 'Recording...' : 'Record Payment'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FeePaymentsPage;
