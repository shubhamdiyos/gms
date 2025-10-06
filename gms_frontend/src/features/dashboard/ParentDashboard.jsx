import { Grid, Paper, Typography, Box, Card, CardContent, Avatar } from '@mui/material';
import {
  Groups,
  EventNote,
  Assessment,
  AttachMoney,
} from '@mui/icons-material';
import { 
  useGetParentProfileQuery,
  useGetParentChildrenQuery,
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

const ParentDashboard = () => {
  const { data: profile } = useGetParentProfileQuery();
  const { data: children = [] } = useGetParentChildrenQuery();

  const stats = [
    {
      title: 'My Children',
      value: children.length,
      icon: <Groups />,
      color: '#1976d2',
      subtitle: 'Enrolled students',
    },
    {
      title: 'Avg Attendance',
      value: '87%',
      icon: <EventNote />,
      color: '#388e3c',
      subtitle: 'This month',
    },
    {
      title: 'Avg Performance',
      value: 'B+',
      icon: <Assessment />,
      color: '#f57c00',
      subtitle: 'Overall grade',
    },
    {
      title: 'Pending Fees',
      value: '₹15,000',
      icon: <AttachMoney />,
      color: '#7b1fa2',
      subtitle: 'Total outstanding',
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Parent Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Welcome back, {profile?.fullName || 'Parent'}
        </Typography>
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
              My Children
            </Typography>
            <Box>
              {children.map((child, index) => (
                <Box
                  key={child.id || index}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    py: 2,
                    borderBottom: '1px solid #e0e0e0',
                    '&:last-child': { borderBottom: 'none' },
                  }}
                >
                  <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                    {child.firstName?.charAt(0) || 'S'}
                  </Avatar>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="subtitle1">
                      {child.fullName || `Student ${index + 1}`}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Class {child.className || '10'} • Roll No: {child.rollNumber || index + 1}
                    </Typography>
                  </Box>
                  <Box sx={{ textAlign: 'right' }}>
                    <Typography variant="body2" color="success.main">
                      85% Attendance
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Grade: A-
                    </Typography>
                  </Box>
                </Box>
              ))}
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Quick Actions
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2">View Attendance</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Check children's attendance
                  </Typography>
                </CardContent>
              </Card>
              
              <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2">View Results</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Check academic performance
                  </Typography>
                </CardContent>
              </Card>
              
              <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2">Pay Fees</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Make fee payments
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

export default ParentDashboard;
