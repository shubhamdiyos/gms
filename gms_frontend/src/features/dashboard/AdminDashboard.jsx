import { Grid, Paper, Typography, Box, Card, CardContent, Avatar, LinearProgress, Alert } from '@mui/material';
import {
  People,
  PersonAdd,
  Class,
  Payment,
  Groups,
  EventNote,
  AttachMoney,
  TrendingUp,
  TrendingDown,
} from '@mui/icons-material';
import {
  useGetSchoolDashboardQuery,
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

const metric = (isLoading, error, value, subtitle) => {
  if (error) return { value: '—', subtitle: 'Data unavailable' };
  if (isLoading) return { value: '—', subtitle: 'Loading…' };
  return { value, subtitle };
};

// Same en-IN rupee formatting the other dashboard cards already use (StudentDashboard).
const rupees = (amount) =>
  `₹${new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(amount)}`;

const AdminDashboard = () => {
  /**
   * SchoolDashboardResponse, counted by the database in one round trip:
   * totalStudents / totalEmployees / totalClassrooms / totalSections / totalExams,
   * feesCollectedAmount / feesUncollectedAmount, plus schoolId and generatedAt.
   */
  const {
    data: schoolAggregate,
    isLoading: aggregateLoading,
    error: aggregateError,
  } = useGetSchoolDashboardQuery();
  const { data: students = [], isLoading: studentsLoading, error: studentsError } = useGetStudentsQuery();
  const { data: employees = [], isLoading: employeesLoading, error: employeesError } = useGetEmployeesQuery();
  // The classroom list is only a lookup for the labels below; every headline number is the aggregate's.
  const { data: classrooms = [] } = useGetClassroomsQuery();

  // A response is an object of numbers; anything else (an empty mock array, an absent body) is not an
  // answer, so the cards stay on em-dash instead of rendering a zero the database never reported.
  const aggregate =
    schoolAggregate && typeof schoolAggregate === 'object' && !Array.isArray(schoolAggregate)
      ? schoolAggregate
      : null;

  const countOf = (field) =>
    aggregate && Number.isFinite(Number(aggregate[field])) ? Number(aggregate[field]) : '—';

  const moneyOf = (field) =>
    aggregate && aggregate[field] != null && Number.isFinite(Number(aggregate[field]))
      ? rupees(Number(aggregate[field]))
      : '—';

  const statCount = (field, subtitle) =>
    metric(aggregateLoading, aggregateError, countOf(field), aggregate ? subtitle : 'No aggregate returned');

  const statMoney = (field, subtitle) =>
    metric(aggregateLoading, aggregateError, moneyOf(field), aggregate ? subtitle : 'No aggregate returned');

  const studentList = Array.isArray(students) ? students : [];
  const employeeList = Array.isArray(employees) ? employees : [];
  const classroomList = Array.isArray(classrooms) ? classrooms : [];

  const classroomById = new Map(classroomList.map((classroom) => [classroom.id, classroom]));
  const recentStudents = [...studentList].sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
  const recentStaff = [...employeeList].sort((a, b) => (b.id ?? 0) - (a.id ?? 0));

  const classroomLabel = (classId) => {
    const classroom = classroomById.get(classId);
    if (classroom) return `${classroom.name} (${classroom.grade}-${classroom.section})`;
    return classId == null ? 'No class assigned' : `Class ${classId}`;
  };

  const displayName = (person) =>
    person.full_name || `${person.firstName ?? ''} ${person.lastName ?? ''}`.trim() || 'Unnamed';

  const generatedAtLabel = () => {
    const raw = aggregate?.generatedAt;
    if (!raw) return null;
    const parsed = new Date(raw);
    return Number.isNaN(parsed.getTime()) ? null : parsed.toLocaleString();
  };

  const stats = [
    {
      ...statCount('totalStudents', 'Enrolled in this school'),
      title: 'Total Students',
      icon: <People />,
      color: '#1976d2',
    },
    {
      ...statCount('totalEmployees', 'Staff on record'),
      title: 'Total Staff',
      icon: <PersonAdd />,
      color: '#388e3c',
    },
    {
      ...statCount('totalClassrooms', 'Listed on the Classes page'),
      title: 'Classrooms',
      icon: <Class />,
      color: '#f57c00',
    },
    {
      ...statCount('totalSections', 'Active sections'),
      title: 'Sections',
      icon: <Groups />,
      color: '#00838f',
    },
    {
      ...statCount('totalExams', 'Active exams'),
      title: 'Exams',
      icon: <EventNote />,
      color: '#5e35b1',
    },
    {
      ...statMoney('feesCollectedAmount', 'Collected to date'),
      title: 'Fees Collected',
      icon: <AttachMoney />,
      color: '#388e3c',
    },
    {
      ...statMoney('feesUncollectedAmount', 'Outstanding balance'),
      title: 'Fees Uncollected',
      icon: <Payment />,
      color: '#7b1fa2',
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

        {aggregateError ? (
          <Alert severity="error" sx={{ mt: 2 }}>
            School totals could not be loaded, so every count below is unknown rather than zero.{' '}
            {aggregateError.message || `GET /dashboard/school failed (${aggregateError.status ?? 'error'}).`}
          </Alert>
        ) : (
          aggregate && (
            <Box sx={{ mt: 2, p: 2, bgcolor: '#e3f2fd', borderRadius: 1 }}>
              <Typography variant="caption" color="primary.main">
                Live database counts for school #{aggregate.schoolId}
                {generatedAtLabel() ? ` • computed ${generatedAtLabel()}` : ''}
                {' | '}GET /dashboard/school
              </Typography>
            </Box>
          )
        )}
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
            ) : studentsError ? (
              <Typography color="error">
                Student data is unavailable. {studentsError.message || 'Please try again.'}
              </Typography>
            ) : recentStudents.length === 0 ? (
              <Typography color="text.secondary">
                No students found. Add students to see them here.
              </Typography>
            ) : (
              <Box>
                {recentStudents.slice(0, 5).map((student) => (
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
                      {displayName(student).charAt(0)}
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle1">
                        {displayName(student)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {student.studentId} • {classroomLabel(student.class_id)}
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
            ) : employeesError ? (
              <Typography color="error">
                Staff data is unavailable. {employeesError.message || 'Please try again.'}
              </Typography>
            ) : recentStaff.length === 0 ? (
              <Typography color="text.secondary">
                No employees found. Add staff members to see them here.
              </Typography>
            ) : (
              <Box>
                {recentStaff.slice(0, 5).map((employee) => (
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
                      {displayName(employee).charAt(0)}
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle1">
                        {displayName(employee)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {employee.employeeId}
                        {employee.designation ? ` • ${employee.designation}` : ''}
                        {employee.department ? ` • ${employee.department}` : ''}
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      {employee.employeeStatus === '1' ? 'Active' : 'Inactive'}
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
