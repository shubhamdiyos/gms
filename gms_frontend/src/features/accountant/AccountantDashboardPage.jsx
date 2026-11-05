import { Box, Typography, Paper, Grid, Card, CardContent, Avatar, LinearProgress, Button } from '@mui/material';
import {
  AttachMoney,
  AccountBalanceWallet,
  TrendingUp,
  People,
  Warning,
  Schedule,
  Receipt,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useGetFinancialDashboardQuery } from '../../store/api/apiSlice';
import { ROUTES } from '../../constants';

const money = (value) =>
  value == null ? '—' : `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

const StatCard = ({ title, value, subtitle, icon, color }) => (
  <Card sx={{ height: '100%' }}>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <Avatar sx={{ bgcolor: color, mr: 2 }}>{icon}</Avatar>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography color="textSecondary" gutterBottom variant="overline">
            {title}
          </Typography>
          <Typography variant="h5" component="h2">
            {value}
          </Typography>
          {subtitle && (
            <Typography color="textSecondary" variant="body2">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
    </CardContent>
  </Card>
);

const AccountantDashboardPage = () => {
  const navigate = useNavigate();
  const { data, isLoading, isError } = useGetFinancialDashboardQuery();

  const collectionRate = data?.feeCollectionRate == null ? null : Number(data.feeCollectionRate);

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Finance Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Fee collection, balances and payment activity for your school
        </Typography>
      </Box>

      {isLoading && <Typography>Loading financial summary...</Typography>}
      {isError && <Typography color="error">Financial summary is unavailable.</Typography>}

      {data && (
        <>
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard
                title="Fees Expected"
                value={money(data.totalFeeExpected)}
                subtitle="Billed to students"
                icon={<Receipt />}
                color="#1976d2"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard
                title="Fees Collected"
                value={money(data.totalFeeCollected)}
                subtitle="Payments received"
                icon={<AccountBalanceWallet />}
                color="#2e7d32"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard
                title="Outstanding"
                value={money(data.totalFeeOutstanding)}
                subtitle="Still to collect"
                icon={<AttachMoney />}
                color="#ed6c02"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard
                title="Collection Rate"
                value={collectionRate == null ? '—' : `${collectionRate.toFixed(1)}%`}
                subtitle={`${data.totalStudents ?? 0} students billed`}
                icon={<TrendingUp />}
                color="#0288d1"
              />
            </Grid>
          </Grid>

          <Grid container spacing={3}>
            <Grid item xs={12} md={7}>
              <Paper sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                  Collection Progress
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(Math.max(collectionRate ?? 0, 0), 100)}
                  sx={{ height: 10, borderRadius: 5, mb: 1 }}
                />
                <Typography variant="body2" color="text.secondary">
                  {money(data.totalFeeCollected)} collected of {money(data.totalFeeExpected)} billed
                  {data.lastUpdated ? ` • updated ${data.lastUpdated}` : ''}
                </Typography>
              </Paper>

              <Paper sx={{ p: 3 }}>
                <Typography variant="h6" gutterBottom>
                  Follow-ups
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Students with pending fees
                    </Typography>
                    <Typography variant="h5">{data.studentsWithPendingFees ?? 0}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Students overdue
                    </Typography>
                    <Typography variant="h5" color="error.main">
                      {data.studentsWithOverdueFees ?? 0}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Overdue invoices
                    </Typography>
                    <Typography variant="h5">{data.overdueInvoices ?? 0}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Upcoming due dates
                    </Typography>
                    <Typography variant="h5">{data.upcomingDueDates ?? 0}</Typography>
                  </Grid>
                </Grid>
              </Paper>
            </Grid>

            <Grid item xs={12} md={5}>
              <Paper sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                  Payments
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      This month
                    </Typography>
                    <Typography variant="h5">{data.paymentsThisMonth ?? 0}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      This week
                    </Typography>
                    <Typography variant="h5">{data.paymentsThisWeek ?? 0}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Today
                    </Typography>
                    <Typography variant="h5">{data.paymentsToday ?? 0}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Average payment
                    </Typography>
                    <Typography variant="h5">{money(data.averagePaymentAmount)}</Typography>
                  </Grid>
                </Grid>
              </Paper>

              <Paper sx={{ p: 3 }}>
                <Typography variant="h6" gutterBottom>
                  Quick Actions
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                  <Button
                    variant="contained"
                    startIcon={<People />}
                    onClick={() => navigate(ROUTES.ACCOUNTANT.PAYMENTS)}
                  >
                    Record a payment
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<Warning />}
                    onClick={() => navigate(ROUTES.ACCOUNTANT.OUTSTANDING)}
                  >
                    Review outstanding fees
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<Schedule />}
                    onClick={() => navigate(ROUTES.ACCOUNTANT.REPORTS)}
                  >
                    Open financial reports
                  </Button>
                </Box>
              </Paper>
            </Grid>
          </Grid>
        </>
      )}
    </Box>
  );
};

export default AccountantDashboardPage;
