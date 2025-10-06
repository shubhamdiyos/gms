import { Grid, Paper, Typography, Box, Card, CardContent, Avatar, LinearProgress } from '@mui/material';
import {
  People,
  PersonAdd,
  Class,
  Payment,
  TrendingUp,
  TrendingDown,
} from '@mui/icons-material';
import { 
  useGetStudentsQuery, 
  useGetEmployeesQuery, 
  useGetClassroomsQuery 
} from '../../store/api/apiSlice';

const StatCard = ({ title, value, icon, color, subtitle, trend }) => (
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
            <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
              {trend && (
                trend > 0 ? (
                  <TrendingUp sx={{ color: '#4caf50', fontSize: 16, mr: 0.5 }} />
                ) : (
                  <TrendingDown sx={{ color: '#f44336', fontSize: 16, mr: 0.5 }} />
                )
              )}
              <Typography color="textSecondary" variant="body2">
                {subtitle}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
    </CardContent>
  </Card>
);

const AdminDashboard = () => {
  const { data: students = [], isLoading: studentsLoading, error: studentsError } = useGetStudentsQuery();
  const { data: employees = [], isLoading: employeesLoading, error: employeesError } = useGetEmployeesQuery();
  const { data: classrooms = [], isLoading: classroomsLoading, error: classroomsError } = useGetClassroomsQuery();

  console.log('👥 Admin Students:', students);
  console.log('👨‍💼 Admin Employees:', employees);
  console.log('🏫 Admin Classrooms:', classrooms);
  console.log('⏳ Admin Loading:', { studentsLoading, employeesLoading, classroomsLoading });
  console.log('❌ Admin Errors:', { studentsError, employeesError, classroomsError });

  const stats = [
    {
      title: 'Total Students',
      value: (students && Array.isArray(students)) ? students.length : 0,
      icon: <People />,
      color: '#1976d2',
      subtitle: '+12 this month',
      trend: 12,
    },
    {
      title: 'Total Staff',
      value: (employees && Array.isArray(employees)) ? employees.length : 0,
      icon: <PersonAdd />,
      color: '#388e3c',
      subtitle: '+3 this month',
      trend: 3,
    },
    {
      title: 'Active Classes',
      value: (classrooms && Array.isArray(classrooms)) ? classrooms.length : 0,
      icon: <Class />,
      color: '#f57c00',
      subtitle: 'All operational',
    },
    {
      title: 'Fee Collection',
      value: '₹2.4L',
      icon: <Payment />,
      color: '#7b1fa2',
      subtitle: '85% collected',
    },
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Admin Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          School management overview
        </Typography>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {stats.map((stat, index) => (
          <Grid item xs={12} sm={6} md={3} key={index}>
            <StatCard {...stat} />
          </Grid>
        ))}
      </Grid>

      {/* Main Content */}
      <Grid container spacing={3}>
        {/* Recent Students */}
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Recent Student Admissions
            </Typography>
            {studentsLoading ? (
              <LinearProgress />
            ) : (!students || students.length === 0) ? (
              <Typography color="text.secondary">
                No students found. Add students to see them here.
              </Typography>
            ) : (
              <Box>
                {(students && Array.isArray(students) ? students : []).slice(0, 5).map((student) => (
                  <Box
                    key={student.id}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      py: 2,
                      borderBottom: '1px solid #e0e0e0',
                      '&:last-child': { borderBottom: 'none' },
                    }}
                  >
                    <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                      {student.firstName?.charAt(0)}
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle1">
                        {student.fullName || `${student.firstName} ${student.lastName}`}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {student.studentId} • Class {student.classroomId}
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      {student.status === '1' ? 'Active' : 'Inactive'}
                    </Typography>
                  </Box>
                ))}
              </Box>
            )}
          </Paper>
        </Grid>

        {/* Staff Overview */}
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Staff Overview
            </Typography>
            {employeesLoading ? (
              <LinearProgress />
            ) : (!employees || employees.length === 0) ? (
              <Typography color="text.secondary">
                No employees found. Add staff members to see them here.
              </Typography>
            ) : (
              <Box>
                {(employees && Array.isArray(employees) ? employees : []).slice(0, 5).map((employee) => (
                  <Box
                    key={employee.id}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      py: 2,
                      borderBottom: '1px solid #e0e0e0',
                      '&:last-child': { borderBottom: 'none' },
                    }}
                  >
                    <Avatar sx={{ bgcolor: '#388e3c', mr: 2 }}>
                      {employee.firstName?.charAt(0)}
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle1">
                        {employee.fullName || `${employee.firstName} ${employee.lastName}`}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {employee.employeeId} • {employee.designation}
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      {employee.status === '1' ? 'Active' : 'Inactive'}
                    </Typography>
                  </Box>
                ))}
              </Box>
            )}
          </Paper>
        </Grid>

        {/* Quick Actions */}
        <Grid item xs={12}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Quick Actions
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={3}>
                <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                  <CardContent sx={{ textAlign: 'center', py: 3 }}>
                    <People sx={{ fontSize: 40, color: '#1976d2', mb: 1 }} />
                    <Typography variant="subtitle1">Add Student</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Register new student
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              
              <Grid item xs={12} sm={6} md={3}>
                <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                  <CardContent sx={{ textAlign: 'center', py: 3 }}>
                    <PersonAdd sx={{ fontSize: 40, color: '#388e3c', mb: 1 }} />
                    <Typography variant="subtitle1">Add Staff</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Add new employee
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              
              <Grid item xs={12} sm={6} md={3}>
                <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                  <CardContent sx={{ textAlign: 'center', py: 3 }}>
                    <Class sx={{ fontSize: 40, color: '#f57c00', mb: 1 }} />
                    <Typography variant="subtitle1">Manage Classes</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Setup classrooms
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              
              <Grid item xs={12} sm={6} md={3}>
                <Card sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}>
                  <CardContent sx={{ textAlign: 'center', py: 3 }}>
                    <Payment sx={{ fontSize: 40, color: '#7b1fa2', mb: 1 }} />
                    <Typography variant="subtitle1">Fee Management</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Manage fee structure
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AdminDashboard;
