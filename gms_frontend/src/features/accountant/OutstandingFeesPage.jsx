import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Paper,
  Typography,
} from '@mui/material';
import { AttachMoney, EventBusy, People } from '@mui/icons-material';
import { DataGrid } from '@mui/x-data-grid';
import dayjs from 'dayjs';
import { useGetStudentFeesQuery } from '../../store/api/apiSlice';
import { ROUTES } from '../../constants';

const money = (value) =>
  value == null ? '—' : `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

const STATUS_META = {
  PENDING: { label: 'Pending', color: 'warning' },
  PARTIAL: { label: 'Partial', color: 'info' },
  OVERDUE: { label: 'Overdue', color: 'error' },
  PAID: { label: 'Paid', color: 'success' },
};

const StatCard = ({ title, value, subtitle, icon, color }) => (
  <Card sx={{ height: '100%' }}>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <Box
          sx={{
            width: 44,
            height: 44,
            borderRadius: '50%',
            bgcolor: color,
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mr: 2,
          }}
        >
          {icon}
        </Box>
        <Box>
          <Typography variant="overline" color="text.secondary">
            {title}
          </Typography>
          <Typography variant="h5">{value}</Typography>
          {subtitle && (
            <Typography variant="body2" color="text.secondary">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
    </CardContent>
  </Card>
);

const OutstandingFeesPage = () => {
  const navigate = useNavigate();
  const { data: studentFees = [], isLoading, isError } = useGetStudentFeesQuery();

  const outstanding = useMemo(
    () => studentFees.filter((fee) => fee.status !== 'PAID' && Number(fee.balanceAmount ?? 0) > 0),
    [studentFees],
  );

  const totalOutstanding = outstanding.reduce((sum, fee) => sum + Number(fee.balanceAmount ?? 0), 0);
  const overdue = outstanding.filter(
    (fee) => fee.status === 'OVERDUE' || (fee.dueDate && dayjs(fee.dueDate).isBefore(dayjs(), 'day')),
  );
  const students = new Set(outstanding.map((fee) => fee.studentId)).size;

  const columns = [
    { field: 'studentName', headerName: 'Student', flex: 1, minWidth: 170 },
    { field: 'feeType', headerName: 'Fee', flex: 1, minWidth: 140 },
    { field: 'academicYear', headerName: 'Year', width: 110 },
    {
      field: 'totalAmount',
      headerName: 'Billed',
      width: 120,
      align: 'right',
      headerAlign: 'right',
      valueFormatter: ({ value }) => money(value),
    },
    {
      field: 'amountPaid',
      headerName: 'Paid',
      width: 120,
      align: 'right',
      headerAlign: 'right',
      valueFormatter: ({ value }) => money(value),
    },
    {
      field: 'balanceAmount',
      headerName: 'Balance',
      width: 130,
      align: 'right',
      headerAlign: 'right',
      renderCell: ({ value }) => (
        <Typography variant="body2" fontWeight={600} color="error.main">
          {money(value)}
        </Typography>
      ),
    },
    {
      field: 'dueDate',
      headerName: 'Due',
      width: 120,
      valueFormatter: ({ value }) => (value ? dayjs(value).format('DD MMM YYYY') : '—'),
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 110,
      renderCell: ({ value }) => {
        const meta = STATUS_META[value] || { label: value || '—', color: 'default' };
        return <Chip size="small" label={meta.label} color={meta.color} />;
      },
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 160,
      sortable: false,
      filterable: false,
      renderCell: ({ row }) => (
        <Button
          size="small"
          onClick={() => navigate(ROUTES.ACCOUNTANT.PAYMENTS, { state: { studentFeeId: row.id } })}
        >
          Record payment
        </Button>
      ),
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Outstanding Fees
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Every student fee that still carries a balance
        </Typography>
      </Box>

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Outstanding fees could not be loaded.
        </Alert>
      )}

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <StatCard
            title="Total Outstanding"
            value={money(totalOutstanding)}
            subtitle={`${outstanding.length} unpaid fee record${outstanding.length === 1 ? '' : 's'}`}
            icon={<AttachMoney />}
            color="#ed6c02"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <StatCard
            title="Students"
            value={students}
            subtitle="With a balance due"
            icon={<People />}
            color="#1976d2"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <StatCard
            title="Overdue"
            value={overdue.length}
            subtitle={`${money(overdue.reduce((sum, fee) => sum + Number(fee.balanceAmount ?? 0), 0))} past due date`}
            icon={<EventBusy />}
            color="#d32f2f"
          />
        </Grid>
      </Grid>

      <Paper sx={{ width: '100%' }}>
        <DataGrid
          rows={outstanding.map((row) => ({ ...row, id: row.id }))}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          pageSizeOptions={[10, 25, 50]}
          sx={{ border: 0, '& .MuiDataGrid-columnHeaders': { backgroundColor: '#f5f5f5' } }}
        />
      </Paper>
    </Box>
  );
};

export default OutstandingFeesPage;
