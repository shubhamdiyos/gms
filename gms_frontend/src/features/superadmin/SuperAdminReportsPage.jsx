import { Box, Grid, Card, CardContent, Typography, Avatar, CircularProgress, Alert, Paper, Chip } from '@mui/material';
import {
  School,
  TrendingUp,
  Block,
  HourglassBottom,
} from '@mui/icons-material';
import { useGetSchoolsQuery } from '../../store/api/apiSlice';

const SCHOOL_STATUS_LABELS = {
  '1': 'Active',
  INACTIVE: 'Inactive',
  SUSPENDED: 'Suspended',
  PENDING_APPROVAL: 'Pending Approval',
};

const StatCard = ({ title, value, icon, color, subtitle }) => (
  <Card sx={{ height: '100%' }}>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <Avatar sx={{ bgcolor: color, mr: 2 }}>
          {icon}
        </Avatar>
        <Box sx={{ flexGrow: 1 }}>
          <Typography color="textSecondary" gutterBottom variant="overline">
            {title}
          </Typography>
          <Typography variant="h4" component="h2">
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

const SuperAdminReportsPage = () => {
  const {
    data: schools = [],
    isLoading: schoolsLoading,
    error: schoolsError,
  } = useGetSchoolsQuery();

  const schoolList = Array.isArray(schools) ? schools : [];
  const totalSchools = schoolList.length;
  const activeSchools = schoolList.filter((s) => s.status === '1').length;
  const inactiveSchools = schoolList.filter((s) => s.status === 'INACTIVE').length;
  const suspendedSchools = schoolList.filter((s) => s.status === 'SUSPENDED').length;
  const pendingApprovalSchools = schoolList.filter((s) => s.status === 'PENDING_APPROVAL').length;

  if (schoolsLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
        <Typography variant="h6" sx={{ ml: 2 }}>
          Loading system reports...
        </Typography>
      </Box>
    );
  }

  if (schoolsError) {
    return (
      <Alert severity="error">
        Error loading schools: {schoolsError.message || 'Failed to load schools data'}
      </Alert>
    );
  }

  const stats = [
    {
      title: 'Total Schools',
      value: totalSchools,
      icon: <School />,
      color: '#1976d2',
      subtitle: 'Every institution on the platform',
    },
    {
      title: 'Active Schools',
      value: activeSchools,
      icon: <TrendingUp />,
      color: '#388e3c',
      subtitle: 'Status Active',
    },
    {
      title: 'Inactive Schools',
      value: inactiveSchools,
      icon: <Block />,
      color: '#757575',
      subtitle: `${suspendedSchools} suspended`,
    },
    {
      title: 'Pending Approval',
      value: pendingApprovalSchools,
      icon: <HourglassBottom />,
      color: '#f57c00',
      subtitle: 'Awaiting activation',
    },
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          System Reports
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Platform-wide school overview for the platform owner.
        </Typography>
      </Box>

      {/* Summary stats */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {stats.map((stat) => (
          <Grid item xs={12} sm={6} md={3} key={stat.title}>
            <StatCard {...stat} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={3}>
        {/* Schools breakdown */}
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Schools Overview
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              All schools on the platform with their registration status.
            </Typography>

            {schoolList.length === 0 && (
              <Typography color="text.secondary">
                No schools found in the system.
              </Typography>
            )}

            {schoolList.length > 0 && (
              <Box>
                {schoolList.map((school) => (
                  <Box
                    key={school.id}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      py: 1.5,
                      borderBottom: '1px solid #e0e0e0',
                      '&:last-child': { borderBottom: 'none' },
                    }}
                  >
                    <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                      <School />
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle1">
                        {school.schoolName}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {school.schoolCode}{' '}
                        {school.principalName ? `• ${school.principalName}` : ''}
                      </Typography>
                    </Box>
                    <Chip
                      label={SCHOOL_STATUS_LABELS[school.status] ?? school.status}
                      color={school.status === '1' ? 'success' : 'default'}
                      size="small"
                    />
                  </Box>
                ))}
              </Box>
            )}
          </Paper>
        </Grid>

        {/* Stats with no platform-wide backend source */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Not Available Platform-Wide
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
              The backend exposes no cross-tenant endpoint for these figures, so they are
              intentionally not shown rather than guessed:
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              <Chip label="Total students" size="small" variant="outlined" />
              <Chip label="Total employees" size="small" variant="outlined" />
              <Chip label="Unread notifications" size="small" variant="outlined" />
              <Chip label="Financial totals" size="small" variant="outlined" />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
              /students, /employees and /notifications are scoped to the caller&apos;s
              school, and /financial-dashboard is restricted to school Admin and
              Accountant roles — none answers a platform-wide query.
            </Typography>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default SuperAdminReportsPage;
