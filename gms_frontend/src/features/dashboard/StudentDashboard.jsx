import { Grid, Paper, Typography, Box, Card, CardContent, Avatar, LinearProgress } from '@mui/material';
import {
  EventNote,
  Assessment,
  AttachMoney,
  Schedule,
} from '@mui/icons-material';
import {
  useGetStudentProfileQuery,
  useGetMyAttendanceQuery,
  useGetResultsForStudentQuery,
  useGetMyFeesQuery,
  useGetMyTimetableQuery,
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

const todayName = () =>
  new Intl.DateTimeFormat('en-US', { weekday: 'long' }).format(new Date()).toUpperCase();

const rupees = (amount) =>
  `₹${new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(amount)}`;

const StudentDashboard = () => {
  const { data: profile, isLoading: profileLoading } = useGetStudentProfileQuery();
  const {
    data: attendance = [],
    isLoading: attendanceLoading,
    error: attendanceError,
  } = useGetMyAttendanceQuery();
  const {
    data: results = [],
    isLoading: resultsLoading,
    error: resultsError,
  } = useGetResultsForStudentQuery(profile?.id, { skip: !profile?.id });
  const { data: fees = [], isLoading: feesLoading, error: feesError } = useGetMyFeesQuery();
  const {
    data: timetable = [],
    isLoading: timetableLoading,
    error: timetableError,
  } = useGetMyTimetableQuery();

  const attendanceList = Array.isArray(attendance) ? attendance : [];
  const resultList = Array.isArray(results) ? results : [];
  const feeList = Array.isArray(fees) ? fees : [];
  const timetableList = Array.isArray(timetable) ? timetable : [];

  const present = attendanceList.filter((record) => record.status === 'PRESENT').length;
  const absent = attendanceList.filter((record) => record.status === 'ABSENT').length;
  const late = attendanceList.filter((record) => record.status === 'LATE').length;
  const attendancePercentage = attendanceList.length
    ? Math.round((present / attendanceList.length) * 100)
    : null;

  const percentages = resultList
    .map((result) => Number(result.percentage))
    .filter((value) => Number.isFinite(value));
  const averagePercentage = percentages.length
    ? percentages.reduce((sum, value) => sum + value, 0) / percentages.length
    : null;

  const amountPaid = feeList.reduce((sum, fee) => sum + Number(fee.amountPaid || 0), 0);
  const paidCount = feeList.filter((fee) => fee.status === 'PAID').length;

  const todaySlots = timetableList
    .flatMap((entry) => (Array.isArray(entry?.slots) ? entry.slots : []))
    .filter((slot) => slot.dayOfWeek === todayName())
    .sort((a, b) => (a.period ?? 0) - (b.period ?? 0));
  const nextSlot = todaySlots[0] ?? null;

  const attendanceDisplay = attendanceError
    ? { value: '—', subtitle: 'Data unavailable' }
    : attendanceLoading
      ? { value: '—', subtitle: 'Loading…' }
      : attendancePercentage === null
        ? { value: '—', subtitle: 'No attendance recorded' }
        : { value: `${attendancePercentage}%`, subtitle: `${present} of ${attendanceList.length} present` };

  const gradeDisplay = resultsError
    ? { value: '—', subtitle: 'Data unavailable' }
    : resultsLoading || (profileLoading && !profile)
      ? { value: '—', subtitle: 'Loading…' }
      : averagePercentage === null
        ? { value: '—', subtitle: 'No results published' }
        : {
            value: `${averagePercentage.toFixed(1)}%`,
            subtitle: `${resultList.length} published ${resultList.length === 1 ? 'result' : 'results'}`,
          };

  const feeDisplay = feesError
    ? { value: '—', subtitle: 'Data unavailable' }
    : feesLoading
      ? { value: '—', subtitle: 'Loading…' }
      : feeList.length === 0
        ? { value: '—', subtitle: 'No fees billed yet' }
        : { value: rupees(amountPaid), subtitle: `${paidCount} of ${feeList.length} cleared` };

  const classDisplay = timetableError
    ? { value: '—', subtitle: 'Timetable unavailable' }
    : timetableLoading
      ? { value: '—', subtitle: 'Loading…' }
      : nextSlot === null
        ? { value: '—', subtitle: 'No class scheduled today' }
        : {
            value: `Period ${nextSlot.period}`,
            subtitle: nextSlot.assignment?.subject?.name || 'Subject not assigned',
          };

  const stats = [
    {
      ...attendanceDisplay,
      icon: <EventNote />,
      color: attendancePercentage === null
        ? '#1976d2'
        : attendancePercentage >= 75
          ? '#388e3c'
          : '#f44336',
      title: 'Attendance',
    },
    {
      ...gradeDisplay,
      icon: <Assessment />,
      color: '#1976d2',
      title: 'Average Grade',
    },
    {
      ...feeDisplay,
      icon: <AttachMoney />,
      color: feeList.length > 0 && paidCount === feeList.length ? '#388e3c' : '#f57c00',
      title: 'Fee Status',
    },
    {
      ...classDisplay,
      icon: <Schedule />,
      color: '#7b1fa2',
      title: 'Next Class',
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Student Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Welcome back, {[profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || 'Student'}
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
            {resultsLoading ? (
              <LinearProgress />
            ) : resultsError ? (
              <Typography color="error">
                Result data is unavailable. {resultsError.message || 'Please try again.'}
              </Typography>
            ) : resultList.length === 0 ? (
              <Typography color="text.secondary">
                No results have been published for you yet.
              </Typography>
            ) : (
              <Box>
                {resultList.slice(0, 5).map((result, index) => (
                  <Box
                    key={result.id ?? index}
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
                        {result.subjectName || `Result ${result.id ?? index + 1}`}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {[
                          result.examName,
                          Number.isFinite(Number(result.obtainedMarks))
                            ? `${result.obtainedMarks} marks`
                            : null,
                        ]
                          .filter(Boolean)
                          .join(' • ')}
                      </Typography>
                    </Box>
                    <Typography variant="h6" color="primary">
                      {result.grade || '—'}
                    </Typography>
                  </Box>
                ))}
              </Box>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Attendance Overview
            </Typography>
            {attendanceLoading ? (
              <LinearProgress />
            ) : attendanceError ? (
              <Typography color="error">
                Attendance data is unavailable. {attendanceError.message || 'Please try again.'}
              </Typography>
            ) : (
              <>
                <Box sx={{ mb: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                    <Typography variant="body2">Overall Attendance</Typography>
                    <Typography variant="body2">
                      {attendancePercentage === null ? 'No records' : `${attendancePercentage}%`}
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={attendancePercentage ?? 0}
                    sx={{ height: 8, borderRadius: 4 }}
                  />
                </Box>

                <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3 }}>
                  <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="h6" color="success.main">{present}</Typography>
                    <Typography variant="body2" color="text.secondary">Present</Typography>
                  </Box>
                  <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="h6" color="error.main">{absent}</Typography>
                    <Typography variant="body2" color="text.secondary">Absent</Typography>
                  </Box>
                  <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="h6" color="warning.main">{late}</Typography>
                    <Typography variant="body2" color="text.secondary">Late</Typography>
                  </Box>
                </Box>

                {attendanceList.length === 0 && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                    No attendance has been marked for your account yet.
                  </Typography>
                )}
              </>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default StudentDashboard;
