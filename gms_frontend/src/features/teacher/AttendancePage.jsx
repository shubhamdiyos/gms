import { useMemo, useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Checkbox,
  Avatar,
  Chip,
  Grid,
  Card,
  CardContent,
  Alert,
  Tooltip,
} from '@mui/material';
import {
  EventNote,
  Save,
  Today,
  Groups,
  CheckCircle,
  Cancel,
} from '@mui/icons-material';
import {
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
  useGetAttendanceQuery,
} from '../../store/api/apiSlice';
import dayjs from 'dayjs';

const SAVE_UNAVAILABLE_REASON =
  'Saving is unavailable: POST /api/v1/attendance rejects a record without a timetableSlotId, and no teacher endpoint returns one.';

const AttendancePage = () => {
  const [selectedClass, setSelectedClass] = useState('');
  const [selectedDate, setSelectedDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [attendanceData, setAttendanceData] = useState({});

  const { data: classes = [] } = useGetTeacherClassesQuery();
  const { data: students = [] } = useGetTeacherStudentsQuery();

  const { data: existingAttendance = [] } = useGetAttendanceQuery(
    { classId: selectedClass, date: selectedDate },
    { skip: !selectedClass || !selectedDate },
  );

  const selectedClassId = selectedClass === '' ? null : Number(selectedClass);

  const classStudents = useMemo(
    () => students.filter((student) => student.classId === selectedClassId),
    [students, selectedClassId],
  );

  const savedStatusByStudent = useMemo(
    () =>
      existingAttendance.reduce((acc, record) => {
        if (record?.student?.id) {
          acc[record.student.id] = record.status;
        }
        return acc;
      }, {}),
    [existingAttendance],
  );

  const handleClassChange = (classId) => {
    setSelectedClass(classId);
    setAttendanceData({});
  };

  const handleAttendanceChange = (studentId, status) => {
    setAttendanceData((prev) => ({
      ...prev,
      [studentId]: prev[studentId] === status ? undefined : status,
    }));
  };

  const getStatus = (studentId) =>
    attendanceData[studentId] ?? savedStatusByStudent[studentId];

  const getAttendanceStats = () => {
    const statuses = classStudents.map((student) => getStatus(student.id));
    return {
      total: classStudents.length,
      marked: statuses.filter(Boolean).length,
      present: statuses.filter((status) => status === 'PRESENT').length,
      absent: statuses.filter((status) => status === 'ABSENT').length,
    };
  };

  const stats = getAttendanceStats();
  const savedCount = classStudents.filter((student) =>
    Boolean(savedStatusByStudent[student.id]),
  ).length;

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Mark Attendance
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Record student attendance for your classes
        </Typography>
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={4}>
            <FormControl fullWidth>
              <InputLabel>Select Class</InputLabel>
              <Select
                value={selectedClass}
                onChange={(e) => handleClassChange(e.target.value)}
                label="Select Class"
              >
                {classes.map((classItem) => (
                  <MenuItem key={classItem.id} value={classItem.id}>
                    {classItem.name} - {classItem.grade} {classItem.section}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12} md={4}>
            <Box
              component="input"
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              sx={{
                width: '100%',
                padding: '16px',
                border: '1px solid #ccc',
                borderRadius: '4px',
                fontSize: '16px',
              }}
            />
          </Grid>

          <Grid item xs={12} md={4}>
            <Tooltip title={SAVE_UNAVAILABLE_REASON}>
              <Box>
                <Button
                  variant="contained"
                  startIcon={<Save />}
                  disabled
                  fullWidth
                  sx={{
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  }}
                >
                  Save Attendance
                </Button>
              </Box>
            </Tooltip>
          </Grid>
        </Grid>
      </Paper>

      {selectedClass && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          {SAVE_UNAVAILABLE_REASON}
        </Alert>
      )}

      {selectedClass && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Groups sx={{ fontSize: 40, color: '#1976d2', mb: 1 }} />
                <Typography variant="h6">{stats.total}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Total Students
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <EventNote sx={{ fontSize: 40, color: '#f57c00', mb: 1 }} />
                <Typography variant="h6">{stats.marked}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Marked
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <CheckCircle sx={{ fontSize: 40, color: '#4caf50', mb: 1 }} />
                <Typography variant="h6">{stats.present}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Present
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Cancel sx={{ fontSize: 40, color: '#f44336', mb: 1 }} />
                <Typography variant="h6">{stats.absent}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Absent
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {selectedClass ? (
        <Paper>
          <Box sx={{ p: 2, borderBottom: '1px solid #e0e0e0' }}>
            <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center' }}>
              <Today sx={{ mr: 1 }} />
              Attendance for {dayjs(selectedDate).format('MMMM DD, YYYY')}
            </Typography>
          </Box>

          {classStudents.length === 0 ? (
            <Box sx={{ p: 4, textAlign: 'center' }}>
              <Typography variant="body1" color="text.secondary">
                No students found in the selected class
              </Typography>
            </Box>
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Student</TableCell>
                    <TableCell>Student ID</TableCell>
                    <TableCell align="center">Present</TableCell>
                    <TableCell align="center">Absent</TableCell>
                    <TableCell align="center">Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {classStudents.map((student) => {
                    const attendanceStatus = getStatus(student.id);
                    const isSaved = Boolean(savedStatusByStudent[student.id]);
                    return (
                      <TableRow key={student.id}>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Avatar sx={{ mr: 2, bgcolor: '#1976d2' }}>
                              {student.firstName?.charAt(0) || 'S'}
                            </Avatar>
                            <Box>
                              <Typography variant="body1">
                                {`${student.firstName ?? ''} ${student.lastName ?? ''}`.trim()}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {student.email}
                              </Typography>
                            </Box>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={student.studentId || student.id}
                            variant="outlined"
                            size="small"
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Checkbox
                            checked={attendanceStatus === 'PRESENT'}
                            onChange={() => handleAttendanceChange(student.id, 'PRESENT')}
                            color="success"
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Checkbox
                            checked={attendanceStatus === 'ABSENT'}
                            onChange={() => handleAttendanceChange(student.id, 'ABSENT')}
                            color="error"
                          />
                        </TableCell>
                        <TableCell align="center">
                          {attendanceStatus ? (
                            <Chip
                              label={isSaved ? `${attendanceStatus} (saved)` : attendanceStatus}
                              color={attendanceStatus === 'PRESENT' ? 'success' : 'error'}
                              size="small"
                            />
                          ) : (
                            <Chip label="Not Marked" variant="outlined" size="small" />
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      ) : (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <EventNote sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            Select a Class to Mark Attendance
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Choose a class from the dropdown above to start marking attendance
          </Typography>
        </Paper>
      )}

      {savedCount > 0 && (
        <Alert severity="info" sx={{ mt: 2 }}>
          {savedCount} attendance record
          {savedCount === 1 ? ' is' : 's are'} already saved for this class on{' '}
          {dayjs(selectedDate).format('MMMM DD, YYYY')}.
        </Alert>
      )}
    </Box>
  );
};

export default AttendancePage;
