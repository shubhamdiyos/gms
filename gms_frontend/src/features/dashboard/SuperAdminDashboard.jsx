import { Grid, Paper, Typography, Box, Card, CardContent, Avatar, CircularProgress, Alert } from '@mui/material';
import {
  School,
  People,
  TrendingUp,
  Assessment,
} from '@mui/icons-material';
import {
  useGetSchoolsQuery,
  useGetNotificationsQuery,
  useGetPlatformDashboardQuery
} from '../../store/api/apiSlice';
import { useAuth } from '../../hooks/useAuth';
import { ROLES } from '../../constants';

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

const SuperAdminDashboard = () => {
  const { hasRole } = useAuth();
  // The platform guard is hasAuthority('SUPERADMIN'); the hook stays skipped for anyone else so the
  // browser never fires a request the backend would answer 403 for.
  const isPlatformOwner = hasRole([ROLES.SUPERADMIN]);

  const {
    data: schools = [],
    isLoading: schoolsLoading,
    error: schoolsError,
  } = useGetSchoolsQuery();
  const {
    data: notifications = [],
    isLoading: notificationsLoading,
    error: notificationsError,
  } = useGetNotificationsQuery();
  const {
    data: platformTotals,
    isLoading: platformLoading,
    error: platformError,
  } = useGetPlatformDashboardQuery(undefined, { skip: !isPlatformOwner });

  const schoolList = Array.isArray(schools) ? schools : [];
  const notificationList = Array.isArray(notifications) ? notifications : [];
  const activeSchools = schoolList.filter((school) => school.status === '1').length;
  const pendingNotifications = notificationList.filter((n) => n.readStatus !== 'READ').length;
  const platformBusy = isPlatformOwner && platformLoading;
  const isLoading = schoolsLoading || notificationsLoading || platformBusy;

  // A platform response is an object of counts; anything else is not an answer, so no card falls back
  // to a zero the database never reported.
  const platform =
    platformTotals && typeof platformTotals === 'object' && !Array.isArray(platformTotals)
      ? platformTotals
      : null;

  const platformValue = (field) => {
    const value = Number(platform?.[field]);
    return Number.isFinite(value) ? value : '—';
  };

  const platformStat = (field, subtitle) => {
    if (!isPlatformOwner) return { value: '—', subtitle: 'Platform totals: SUPERADMIN only' };
    if (platformError) return { value: '—', subtitle: 'Data unavailable' };
    if (platformBusy) return { value: '—', subtitle: 'Loading…' };
    const value = platformValue(field);
    return { value, subtitle: platform ? subtitle : 'No aggregate returned' };
  };

  const generatedAtLabel = () => {
    const raw = platform?.generatedAt;
    if (!raw) return null;
    const parsed = new Date(raw);
    return Number.isNaN(parsed.getTime()) ? null : parsed.toLocaleString();
  };

  const stats = [
    {
      ...platformStat('totalSchools', 'Schools on the platform'),
      title: 'Total Schools',
      icon: <School />,
      color: '#1976d2',
    },
    {
      ...platformStat('totalStudents', 'Across every school'),
      title: 'Total Students',
      icon: <People />,
      color: '#388e3c',
    },
    {
      ...platformStat('totalEmployees', 'Across every school'),
      title: 'Total Employees',
      icon: <TrendingUp />,
      color: '#f57c00',
    },
    {
      ...platformStat('totalUsers', 'Active system accounts'),
      title: 'Total Users',
      icon: <Assessment />,
      color: '#7b1fa2',
    },
  ];

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
        <Typography variant="h6" sx={{ ml: 2 }}>
          Loading dashboard data...
        </Typography>
      </Box>
    );
  }

  if (schoolsError || notificationsError) {
    return (
      <Box>
        <Alert severity="error" sx={{ mb: 2 }}>
          Error loading dashboard data. Please try refreshing the page.
        </Alert>
        {schoolsError && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            Schools: {schoolsError.message || 'Failed to load schools data'}
          </Alert>
        )}
        {notificationsError && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            Notifications: {notificationsError.message || 'Failed to load notifications data'}
          </Alert>
        )}
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          SuperAdmin Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          System-wide overview and management
        </Typography>

        {platformError ? (
          <Alert severity="error" sx={{ mt: 2 }}>
            Platform totals could not be loaded, so the counts below are unknown rather than zero.{' '}
            {platformError.message ||
              `GET /dashboard/platform failed (${platformError.status ?? 'error'}).`}
          </Alert>
        ) : (
          <Box sx={{ mt: 2, p: 2, bgcolor: '#e3f2fd', borderRadius: 1 }}>
            <Typography variant="caption" color="primary.main">
              Live API data: {schoolList.length} {schoolList.length === 1 ? 'school' : 'schools'}
              listed by GET /schools
              {' | '}{pendingNotifications} unread notification{pendingNotifications === 1 ? '' : 's'}
              {' | '}GET /dashboard/platform: {platformValue('totalSchools')} schools,
              {' '}{platformValue('totalUsers')} users, {platformValue('totalStudents')} students,
              {' '}{platformValue('totalEmployees')} employees
              {generatedAtLabel() ? ` • computed ${generatedAtLabel()}` : ''}
            </Typography>
          </Box>
        )}
      </Box>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        {stats.map((stat, index) => (
          <Grid item xs={12} sm={6} md={3} key={index}>
            <StatCard {...stat} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Recent Schools
            </Typography>
            {schoolsError ? (
              <Typography color="error">
                School data is unavailable. {schoolsError.message || 'Please try again.'}
              </Typography>
            ) : schoolList.length === 0 ? (
              <Typography color="text.secondary">
                No schools are registered in the database yet.
              </Typography>
            ) : (
              <Box>
                {[...schoolList]
                  .sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
                  .slice(0, 5)
                  .map((school) => (
                    <Box
                      key={school.id}
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        py: 2,
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
                          {school.schoolCode}
                          {school.principalName ? ` • ${school.principalName}` : ''}
                        </Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary">
                        {school.status === '1' ? 'Active' : 'Inactive'}
                      </Typography>
                    </Box>
                  ))}
              </Box>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              System Overview
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Card sx={{ bgcolor: '#e8f5e8' }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2" color="success.main">Live School Records</Typography>
                  <Typography variant="body2" color="text.secondary">
                    GET /schools returned {schoolList.length} {schoolList.length === 1 ? 'school' : 'schools'}
                    ({activeSchools} active)
                  </Typography>
                </CardContent>
              </Card>

              <Card sx={{ bgcolor: platformError ? '#ffebee' : '#fff3e0' }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2" color={platformError ? 'error.main' : 'warning.main'}>
                    Cross-Tenant Counts
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {platform
                      ? `GET /dashboard/platform counts ${platformValue('totalStudents')} students and ${platformValue('totalEmployees')} staff across ${platformValue('totalSchools')} schools, with ${platformValue('totalUsers')} system accounts.`
                      : platformBusy
                        ? 'Reading GET /dashboard/platform…'
                        : 'No platform counts to show: this route is SUPERADMIN-only, so the request was not made.'}
                  </Typography>
                </CardContent>
              </Card>

              <Card sx={{ bgcolor: '#e3f2fd' }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2" color="primary.main">Notification Queue</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {notificationList.length} pending {notificationList.length === 1 ? 'delivery' : 'deliveries'}
                    , {pendingNotifications} unread
                  </Typography>
                </CardContent>
              </Card>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default SuperAdminDashboard;
