import { Box, Grid, Card, CardContent, Typography, Avatar, CircularProgress, Alert, Paper, Divider, Chip } from '@mui/material';
import {
  School,
  People,
  TrendingUp,
  NotificationsActive,
  Assessment,
} from '@mui/icons-material';
import {
  useGetSchoolsQuery,
  useGetStudentsQuery,
  useGetEmployeesQuery,
  useGetNotificationsQuery,
} from '../../store/api/apiSlice';

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
  const {
    data: students = [],
    isLoading: studentsLoading,
    error: studentsError,
  } = useGetStudentsQuery();
  const {
    data: employees = [],
    isLoading: employeesLoading,
    error: employeesError,
  } = useGetEmployeesQuery();
  const {
    data: notifications = [],
    isLoading: notificationsLoading,
    error: notificationsError,
  } = useGetNotificationsQuery();

  const isLoading =
    schoolsLoading || studentsLoading || employeesLoading || notificationsLoading;

  const totalSchools = Array.isArray(schools) ? schools.length : 0;
  const activeSchools = Array.isArray(schools)
    ? schools.filter((s) => s.status === '1').length
    : 0;
  const inactiveSchools = totalSchools - activeSchools;

  const totalStudents = Array.isArray(students) ? students.length : 0;
  const totalEmployees = Array.isArray(employees) ? employees.length : 0;

  const pendingNotifications = Array.isArray(notifications)
    ? notifications.filter((n) => n.read_status !== 'read').length
    : 0;

  const stats = [
    {
      title: 'Total Schools',
      value: totalSchools,
      icon: <School />,
      color: '#1976d2',
      subtitle: 'Registered institutions',
    },
    {
      title: 'Active Schools',
      value: activeSchools,
      icon: <TrendingUp />,
      color: '#388e3c',
      subtitle: `${inactiveSchools} inactive`,
    },
    {
      title: 'Total Students',
      value: totalStudents,
      icon: <People />,
      color: '#f57c00',
      subtitle: 'Across all schools (current scope)',
    },
    {
      title: 'Total Employees',
      value: totalEmployees,
      icon: <Assessment />,
      color: '#7b1fa2',
      subtitle: 'Teachers & staff',
    },
  ];

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
        <Typography variant="h6" sx={{ ml: 2 }}>
          Loading system reports...
        </Typography>
      </Box>
    );
  }

  if (schoolsError || studentsError || employeesError || notificationsError) {
    return (
      <Box>
        <Alert severity="error" sx={{ mb: 2 }}>
          Error loading system reports. Please try refreshing the page.
        </Alert>
        {schoolsError && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            Schools: {schoolsError.message || 'Failed to load schools data'}
          </Alert>
        )}
        {studentsError && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            Students: {studentsError.message || 'Failed to load students data'}
          </Alert>
        )}
        {employeesError && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            Employees: {employeesError.message || 'Failed to load employees data'}
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
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          System Reports
        </Typography>
        <Typography variant="body1" color="text.secondary">
          High-level overview of schools, users, and notifications for SuperAdmin.
        </Typography>
      </Box>

      {/* Summary stats */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {stats.map((stat, index) => (
          <Grid item xs={12} sm={6} md={3} key={index}>
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
              List of all schools accessible in the current context with status.
            </Typography>

            {(!schools || !Array.isArray(schools) || schools.length === 0) && (
              <Typography color="text.secondary">
                No schools found in the system.
              </Typography>
            )}

            {Array.isArray(schools) && schools.length > 0 && (
              <Box>
                {schools.map((school) => (
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
                      label={school.status === '1' ? 'Active' : 'Inactive'}
                      color={school.status === '1' ? 'success' : 'default'}
                      size="small"
                    />
                  </Box>
                ))}
              </Box>
            )}
          </Paper>
        </Grid>

        {/* Notifications and activity */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom>
              Notifications Summary
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <Avatar sx={{ bgcolor: '#ff9800', mr: 2 }}>
                <NotificationsActive />
              </Avatar>
              <Box>
                <Typography variant="subtitle1">
                  {pendingNotifications}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Pending / unread notifications
                </Typography>
              </Box>
            </Box>
            <Divider sx={{ my: 2 }} />
            <Typography variant="body2" color="text.secondary">
              Use the notifications panel in the top bar to view and manage
              individual notifications.
            </Typography>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              System Activity Snapshot
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">
                  Total schools
                </Typography>
                <Typography variant="body2">{totalSchools}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">
                  Active schools
                </Typography>
                <Typography variant="body2">{activeSchools}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">
                  Total students
                </Typography>
                <Typography variant="body2">{totalStudents}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">
                  Total employees
                </Typography>
                <Typography variant="body2">{totalEmployees}</Typography>
              </Box>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default SuperAdminReportsPage;
