import { Grid, Paper, Typography, Box, Card, CardContent, Avatar, LinearProgress } from '@mui/material';
import {
  Groups,
  EventNote,
  Grade,
  Schedule,
} from '@mui/icons-material';
import {
  useGetTeacherProfileQuery,
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
  useGetTeacherTimetableQuery,
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

const metric = (isLoading, error, value, subtitle) => {
  if (error) return { value: '—', subtitle: 'Data unavailable' };
  if (isLoading) return { value: '—', subtitle: 'Loading…' };
  return { value, subtitle };
};

const todayName = () =>
  new Intl.DateTimeFormat('en-US', { weekday: 'long' }).format(new Date()).toUpperCase();

const TeacherDashboard = () => {
  const { data: profile } = useGetTeacherProfileQuery();
  const {
    data: classes = [],
    isLoading: classesLoading,
    error: classesError,
  } = useGetTeacherClassesQuery();
  const {
    data: students = [],
    isLoading: studentsLoading,
    error: studentsError,
  } = useGetTeacherStudentsQuery();
  const { data: timetable, isLoading: timetableLoading, error: timetableError } =
    useGetTeacherTimetableQuery();

  const classList = Array.isArray(classes) ? classes : [];
  const studentList = Array.isArray(students) ? students : [];
  const uniqueStudentIds = new Set(studentList.map((student) => student.id));
  const slots = Array.isArray(timetable?.slots) ? timetable.slots : null;
  const todayPeriods = slots ? slots.filter((slot) => slot.dayOfWeek === todayName()) : null;

  const studentsInClass = (classId) =>
    studentList.filter((student) => student.classId === classId).length;

  const classDescription = (classItem) => {
    const parts = [];
    if (classItem.grade) parts.push(`Grade ${classItem.grade}`);
    if (classItem.section) parts.push(`Section ${classItem.section}`);
    return parts.join(' • ');
  };

  const stats = [
    {
      ...metric(
        classesLoading,
        classesError,
        classList.length,
        classList.length === 0 ? 'No classrooms assigned' : 'Assigned classrooms'
      ),
      title: 'My Classes',
      icon: <Groups />,
      color: '#1976d2',
    },
    {
      ...metric(
        studentsLoading,
        studentsError,
        uniqueStudentIds.size,
        uniqueStudentIds.size === 0
          ? 'No students in your classes'
          : `${classList.length} assigned ${classList.length === 1 ? 'class' : 'classes'}`
      ),
      title: 'Students',
      icon: <EventNote />,
      color: '#388e3c',
    },
    {
      title: 'Pending Grades',
      value: '—',
      icon: <Grade />,
      color: '#f57c00',
      subtitle: 'Awaiting result aggregate',
    },
    {
      ...(timetableError
        ? { value: '—', subtitle: 'Timetable unavailable' }
        : timetableLoading
          ? { value: '—', subtitle: 'Loading…' }
          : todayPeriods === null
            ? { value: '—', subtitle: 'No timetable published' }
            : {
                value: todayPeriods.length,
                subtitle: todayPeriods.length === 0
                  ? 'No periods scheduled today'
                  : `Periods ${todayPeriods
                      .map((slot) => slot.period)
                      .sort((a, b) => a - b)
                      .join(', ')}`,
              }),
      title: 'Today\'s Classes',
      icon: <Schedule />,
      color: '#7b1fa2',
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
            {classesLoading ? (
              <LinearProgress />
            ) : classesError ? (
              <Typography color="error">
                Class data is unavailable. {classesError.message || 'Please try again.'}
              </Typography>
            ) : classList.length === 0 ? (
              <Typography color="text.secondary">
                No classrooms are assigned to your account yet.
              </Typography>
            ) : (
              <Box>
                {classList.map((classItem, index) => {
                  const count = studentsInClass(classItem.id);
                  const details = [classDescription(classItem)];
                  if (!studentsLoading && !studentsError) {
                    details.push(`${count} ${count === 1 ? 'student' : 'students'}`);
                  }
                  return (
                    <Box
                      key={classItem.id ?? index}
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
                          {classItem.name || `Classroom ${classItem.id ?? index + 1}`}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {details.filter(Boolean).join(' • ')}
                        </Typography>
                      </Box>
                    </Box>
                  );
                })}
              </Box>
            )}
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
