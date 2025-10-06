import { Grid, Paper, Typography, Box, Card, CardContent, Avatar } from '@mui/material';
import {
  Groups,
  EventNote,
  Grade,
  Schedule,
} from '@mui/icons-material';
import { 
  useGetTeacherProfileQuery,
  useGetTeacherClassesQuery,
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

const TeacherDashboard = () => {
  const { data: profile } = useGetTeacherProfileQuery();
  const { data: classes = [] } = useGetTeacherClassesQuery();

  const stats = [
    {
      title: 'My Classes',
      value: classes.length,
      icon: <Groups />,
      color: '#1976d2',
      subtitle: 'Active classes',
    },
    {
      title: 'Students',
      value: '156',
      icon: <EventNote />,
      color: '#388e3c',
      subtitle: 'Total students',
    },
    {
      title: 'Pending Grades',
      value: '12',
      icon: <Grade />,
      color: '#f57c00',
      subtitle: 'Need attention',
    },
    {
      title: 'Today\'s Classes',
      value: '4',
      icon: <Schedule />,
      color: '#7b1fa2',
      subtitle: 'Scheduled',
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Teacher Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Welcome back, {profile?.fullName || 'Teacher'}
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
              My Classes
            </Typography>
            <Box>
              {classes.map((classItem, index) => (
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
                    <Groups />
                  </Avatar>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="subtitle1">
                      Class {classItem.className || index + 1}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {classItem.subject || 'Mathematics'} • {classItem.students || '25'} students
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
                  <Typography variant="subtitle2">Mark Attendance</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Record student attendance
                  </Typography>
                </CardContent>
              </Card>
              
              <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                <CardContent sx={{ py: 2 }}>
                  <Typography variant="subtitle2">Enter Grades</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Update student grades
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

export default TeacherDashboard;
