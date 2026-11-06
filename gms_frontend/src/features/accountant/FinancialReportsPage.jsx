import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Add, Assessment, Paid, AccountBalance } from '@mui/icons-material';
import { DataGrid } from '@mui/x-data-grid';
import dayjs from 'dayjs';
import { toast } from 'react-toastify';
import {
  useCreateFinancialReportMutation,
  useGetFinancialDashboardQuery,
  useGetFinancialReportsQuery,
} from '../../store/api/apiSlice';
import { useAuth } from '../../hooks/useAuth';
import { ROLES } from '../../constants';

const money = (value) =>
  value == null ? '—' : `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

const academicYearFor = (date) => {
  const year = date.year();
  return `${year}-${year + 1}`;
};

const emptyForm = () => {
  const today = dayjs();
  return {
    reportName: `${today.format('MMMM YYYY')} Fee Collection`,
    reportType: 'MONTHLY',
    reportPeriod: today.format('MMMM YYYY'),
    academicYear: academicYearFor(today),
    expenses: '',
  };
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

const FinancialReportsPage = () => {
  const { user, getUserRole } = useAuth();
  const isAdmin = getUserRole() === ROLES.ADMIN;

  const { data: reports = [], isLoading, isError } = useGetFinancialReportsQuery();
  const { data: dashboard } = useGetFinancialDashboardQuery(undefined, { skip: !isAdmin });
  const [createReport, { isLoading: isSaving }] = useCreateFinancialReportMutation();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const totals = useMemo(
    () =>
      reports.reduce(
        (acc, report) => ({
          collected: acc.collected + Number(report.collectedFees ?? 0),
          outstanding: acc.outstanding + Number(report.outstandingFees ?? 0),
          net: acc.net + Number(report.netProfit ?? 0),
        }),
        { collected: 0, outstanding: 0, net: 0 },
      ),
    [reports],
  );

  const collectedFees = Number(dashboard?.totalFeeCollected ?? 0);
  const outstandingFees = Number(dashboard?.totalFeeOutstanding ?? 0);
  const totalIncome = Number(dashboard?.totalIncome ?? collectedFees);
  const totalExpenses = Number(form.expenses || 0);
  const netProfit = totalIncome - totalExpenses;

  const handleField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
  };

  const handleGenerate = async () => {
    if (!form.reportName.trim()) {
      toast.error('Report name is required');
      return;
    }
    try {
      await createReport({
        reportName: form.reportName.trim(),
        reportType: form.reportType,
        reportPeriod: form.reportPeriod,
        academicYear: form.academicYear,
        generatedDate: dayjs().format('YYYY-MM-DD'),
        totalIncome,
        totalExpenses,
        netProfit,
        outstandingFees,
        collectedFees,
        generatedBy:
          user?.fullName ||
          [user?.firstName, user?.lastName].filter(Boolean).join(' ') ||
          user?.username ||
          'Administrator',
      }).unwrap();
      toast.success('Financial report generated');
      setDialogOpen(false);
    } catch (error) {
      toast.error(error?.data?.message || 'Could not generate the report');
    }
  };

  const columns = [
    { field: 'reportName', headerName: 'Report', flex: 1.2, minWidth: 180 },
    {
      field: 'reportType',
      headerName: 'Type',
      width: 110,
      renderCell: ({ value }) => (value ? <Chip size="small" label={value} /> : '—'),
    },
    { field: 'reportPeriod', headerName: 'Period', width: 140 },
    { field: 'academicYear', headerName: 'Year', width: 110 },
    {
      field: 'generatedDate',
      headerName: 'Generated',
      width: 130,
      valueFormatter: ({ value }) => (value ? dayjs(value).format('DD MMM YYYY') : '—'),
    },
    {
      field: 'collectedFees',
      headerName: 'Collected',
      width: 130,
      align: 'right',
      headerAlign: 'right',
      valueFormatter: ({ value }) => money(value),
    },
    {
      field: 'outstandingFees',
      headerName: 'Outstanding',
      width: 130,
      align: 'right',
      headerAlign: 'right',
      valueFormatter: ({ value }) => money(value),
    },
    {
      field: 'netProfit',
      headerName: 'Net',
      width: 130,
      align: 'right',
      headerAlign: 'right',
      valueFormatter: ({ value }) => money(value),
    },
    { field: 'generatedBy', headerName: 'Generated By', width: 150 },
  ];

  return (
    <Box>
      <Box
        sx={{
          mb: 3,
          display: 'flex',
          flexWrap: 'wrap',
          gap: 2,
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Financial Reports
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {isAdmin
              ? 'Generated from the current fee position'
              : 'Reports generated for your school'}
          </Typography>
        </Box>
        {isAdmin && (
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => {
              setForm(emptyForm());
              setDialogOpen(true);
            }}
          >
            Generate Report
          </Button>
        )}
      </Box>

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Financial reports could not be loaded.
        </Alert>
      )}

      {reports.length > 0 && (
        <Grid container spacing={3} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={4}>
            <StatCard
              title="Reports"
              value={reports.length}
              subtitle="On file for this school"
              icon={<Assessment />}
              color="#1976d2"
            />
          </Grid>
          <Grid item xs={12} sm={4}>
            <StatCard
              title="Collected (sum)"
              value={money(totals.collected)}
              subtitle={`${money(totals.outstanding)} outstanding (sum)`}
              icon={<Paid />}
              color="#2e7d32"
            />
          </Grid>
          <Grid item xs={12} sm={4}>
            <StatCard
              title="Net (sum)"
              value={money(totals.net)}
              subtitle="Across the reports on file"
              icon={<AccountBalance />}
              color="#0288d1"
            />
          </Grid>
        </Grid>
      )}

      <Paper sx={{ width: '100%' }}>
        <DataGrid
          rows={reports.map((row) => ({ ...row, id: row.id }))}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          pageSizeOptions={[10, 25, 50]}
          sx={{ border: 0, '& .MuiDataGrid-columnHeaders': { backgroundColor: '#f5f5f5' } }}
        />
      </Paper>

      {isAdmin ? (
        <Alert severity="info" sx={{ mt: 2 }}>
          A report snapshots the fee totals at the moment it is generated; payments recorded
          afterwards are reflected in the next report.
        </Alert>
      ) : (
        <Alert severity="info" sx={{ mt: 2 }}>
          Generating, editing and deleting financial reports stays with the school administrator;
          the accountant portal is read-only for reports.
        </Alert>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Generate Financial Report</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Report name"
              value={form.reportName}
              onChange={handleField('reportName')}
              required
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                select
                label="Type"
                value={form.reportType}
                onChange={handleField('reportType')}
                sx={{ flex: 1 }}
              >
                {['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUAL'].map((type) => (
                  <MenuItem key={type} value={type}>
                    {type}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="Period"
                value={form.reportPeriod}
                onChange={handleField('reportPeriod')}
                sx={{ flex: 1 }}
              />
              <TextField
                label="Academic year"
                value={form.academicYear}
                onChange={handleField('academicYear')}
                sx={{ flex: 1 }}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="Collected fees" value={money(collectedFees)} disabled fullWidth />
              <TextField
                label="Outstanding fees"
                value={money(outstandingFees)}
                disabled
                fullWidth
              />
            </Stack>
            <TextField
              label="Expenses recorded for this period"
              type="number"
              value={form.expenses}
              onChange={handleField('expenses')}
              helperText={`Income ${money(totalIncome)} − expenses = net ${money(netProfit)}`}
              inputProps={{ min: 0, step: '0.01' }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleGenerate} disabled={isSaving}>
            {isSaving ? 'Generating…' : 'Generate'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FinancialReportsPage;
