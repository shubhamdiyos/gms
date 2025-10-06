import { Grid, Paper, Typography, Box, Card, CardContent, Avatar, LinearProgress } from '@mui/material';
import {
  Person,
  EventNote,
  Assessment,
  AttachMoney,
  Schedule,
} from '@mui/icons-material';
import { 
  useGetStudentProfileQuery,
  useGetMyAttendanceQuery,
  useGetMyResultsQuery,
  useGetMyFeesQuery,
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

const StudentDashboard = () => {
  const { data: profile } = useGetStudentProfileQuery();
  const { data: attendance = [] } = useGetMyAttendanceQuery();
  const { data: results = [] } = useGetMyResultsQuery();
  const { data: fees = [] } = useGetMyFeesQuery();

  const attendancePercentage = 85; // Calculate from attendance data
  const totalFees = fees.reduce((sum, fee) => sum + (fee.amount || 0), 0);
  const paidFees = fees.reduce((sum, fee) => sum + (fee.paidAmount || 0), 0);

  const stats = [
    {
      title: 'Attendance',
      value: `${attendancePercentage}%`,
      icon: <EventNote />,
      color: attendancePercentage >= 75 ? '#388e3c' : '#f44336',
      subtitle: 'This semester',
    },
    {
      title: 'Average Grade',
      value: 'A-',
      icon: <Assessment />,
      color: '#1976d2',
      subtitle: 'Current performance',
    },
    {
      title: 'Fee Status',
      value: `₹${totalFees - paidFees}`,
      icon: <AttachMoney />,
      color: totalFees === paidFees ? '#388e3c' : '#f57c00',
      subtitle: 'Outstanding',
    },
    {
      title: 'Next Class',
      value: '10:30 AM',
      icon: <Schedule />,
      color: '#7b1fa2',
      subtitle: 'Mathematics',
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Student Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Welcome back, {profile?.fullName || 'Student'}
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
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Recent Results
            </Typography>
            <Box>
              {results.slice(0, 5).map((result, index) => (
                <Box
                  key={index}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    py: 2,
                    borderBottom: '1px solid #e0e0e0',
                    '&:last-child': { borderBottom: 'none' },
                  }}
                >
                  <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                    <Assessment />
                  </Avatar>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="subtitle1">
                      {result.subject || `Subject ${index + 1}`}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {result.examType || 'Mid-term Exam'}
                    </Typography>
                  </Box>
                  <Typography variant="h6" color="primary">
                    {result.grade || 'A'}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Attendance Overview
            </Typography>
            <Box sx={{ mb: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2">Overall Attendance</Typography>
                <Typography variant="body2">{attendancePercentage}%</Typography>
              </Box>
              <LinearProgress 
                variant="determinate" 
                value={attendancePercentage} 
                sx={{ height: 8, borderRadius: 4 }}
              />
            </Box>
            
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3 }}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h6" color="success.main">120</Typography>
                <Typography variant="body2" color="text.secondary">Present</Typography>
              </Box>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h6" color="error.main">20</Typography>
                <Typography variant="body2" color="text.secondary">Absent</Typography>
              </Box>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h6" color="warning.main">5</Typography>
                <Typography variant="body2" color="text.secondary">Late</Typography>
              </Box>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default StudentDashboard;
